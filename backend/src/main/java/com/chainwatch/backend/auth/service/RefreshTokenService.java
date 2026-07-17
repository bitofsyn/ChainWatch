package com.chainwatch.backend.auth.service;

import com.chainwatch.backend.audit.service.AuditLogService;
import com.chainwatch.backend.auth.domain.RefreshToken;
import com.chainwatch.backend.auth.exception.InvalidRefreshTokenException;
import com.chainwatch.backend.auth.repository.RefreshTokenRepository;
import com.chainwatch.backend.security.SecurityProperties;
import com.chainwatch.backend.user.domain.UserAccount;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회전식 리프레시 토큰 관리. 원문은 256비트 랜덤(base64url)이며 DB에는 SHA-256 해시만 저장한다.
 * 회전이 끝난 토큰이 다시 제시되면 탈취 신호로 간주해 해당 사용자의 모든 세션을 폐기한다.
 * 멀티 세션(브라우저별 토큰)을 허용한다.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private static final int TOKEN_BYTES = 32;
    private static final Duration STALE_RETENTION = Duration.ofDays(30);

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityProperties properties;
    private final AuditLogService auditLogService;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            SecurityProperties properties,
            AuditLogService auditLogService
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.properties = properties;
        this.auditLogService = auditLogService;
    }

    /** 새 리프레시 토큰을 발급하고 원문을 반환한다(저장은 해시만). */
    @Transactional
    public String issue(UserAccount user) {
        byte[] raw = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(raw);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant now = Instant.now();
        refreshTokenRepository.save(new RefreshToken(
                hash(rawToken), user, now, now.plus(Duration.ofDays(properties.refreshExpirationDays()))));
        return rawToken;
    }

    /**
     * 토큰 회전: 기존 토큰 검증 → 폐기(교체 해시 연결) → 새 토큰 발급.
     * 재사용 감지 시 사용자 전체 세션을 폐기하고 감사 기록을 남긴다.
     * noRollbackFor: 재사용 감지 후 401을 던져도 세션 폐기와 감사 기록은 커밋되어야 한다.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RotationResult rotate(String rawToken) {
        RefreshToken current = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (current.isReused()) {
            // bulk revoke가 영속성 컨텍스트를 비우기 전에 LAZY user 필드를 먼저 읽어둔다
            UserAccount user = current.getUser();
            Long userId = user.getId();
            String username = user.getUsername();
            String authority = user.getRole().authority();
            int revokedCount = refreshTokenRepository.revokeAllByUserId(userId);
            auditLogService.record(username, authority, "REFRESH_TOKEN_REUSE", "AUTH", username,
                    "Rotated refresh token was presented again; revoked " + revokedCount + " active sessions");
            log.warn("[REFRESH_TOKEN_REUSE] user={} — revoked {} active sessions", username, revokedCount);
            throw new InvalidRefreshTokenException();
        }
        if (current.isRevoked() || current.isExpired(Instant.now()) || !current.getUser().isActive()) {
            throw new InvalidRefreshTokenException();
        }

        UserAccount user = current.getUser();
        String newRawToken = issue(user);
        current.markReplacedBy(hash(newRawToken));
        return new RotationResult(user, newRawToken);
    }

    /** 로그아웃: 제시된 토큰만 폐기한다(다른 세션은 유지). 이미 무효면 조용히 무시. */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(RefreshToken::revoke);
    }

    /** 비활성화·비밀번호 변경 시 해당 사용자의 모든 세션을 즉시 무효화한다. */
    @Transactional
    public void revokeAllForUser(UserAccount user) {
        refreshTokenRepository.revokeAllByUserId(user.getId());
    }

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void cleanUpStaleTokens() {
        int deleted = refreshTokenRepository.deleteStaleTokens(Instant.now().minus(STALE_RETENTION));
        if (deleted > 0) {
            log.info("[REFRESH_TOKEN_CLEANUP] deleted {} stale refresh tokens", deleted);
        }
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required but unavailable", exception);
        }
    }

    public record RotationResult(UserAccount user, String newRefreshToken) {
    }
}
