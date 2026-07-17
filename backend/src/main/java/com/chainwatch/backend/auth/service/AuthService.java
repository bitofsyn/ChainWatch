package com.chainwatch.backend.auth.service;

import com.chainwatch.backend.audit.service.AuditLogService;
import com.chainwatch.backend.auth.api.LoginRequest;
import com.chainwatch.backend.auth.api.LoginResponse;
import com.chainwatch.backend.auth.api.PasswordChangeRequest;
import com.chainwatch.backend.auth.api.UserSummary;
import com.chainwatch.backend.auth.exception.AuthenticationRequiredException;
import com.chainwatch.backend.auth.exception.InvalidCredentialsException;
import com.chainwatch.backend.auth.exception.InvalidRefreshTokenException;
import com.chainwatch.backend.security.JwtTokenProvider;
import com.chainwatch.backend.user.domain.UserAccount;
import com.chainwatch.backend.user.repository.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB(user_accounts) 기반 인증. 로그인 시 액세스 JWT + 회전식 리프레시 토큰을 발급한다.
 * 비활성 계정·미존재 계정·비밀번호 불일치는 모두 동일한 401로 응답해 사용자 열거를 막는다.
 */
@Service
public class AuthService {

    private static final String AUDIT_ACTION_LOGIN_SUCCESS = "LOGIN_SUCCESS";
    private static final String AUDIT_ACTION_LOGIN_FAILURE = "LOGIN_FAILURE";
    private static final String AUDIT_ACTION_LOGOUT = "LOGOUT";
    private static final String AUDIT_ACTION_PASSWORD_CHANGE = "PASSWORD_CHANGE";
    private static final String AUDIT_TARGET_TYPE_AUTH = "AUTH";

    private final UserAccountRepository userAccountRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public AuthService(
            UserAccountRepository userAccountRepository,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenService refreshTokenService,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    /** noRollbackFor: 로그인 실패 시에도 LOGIN_FAILURE 감사 기록은 커밋되어야 한다. */
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public LoginResponse login(LoginRequest request) {
        Optional<UserAccount> found = userAccountRepository.findByUsername(request.username());
        if (found.isEmpty()
                || !found.get().isActive()
                || !passwordEncoder.matches(request.password(), found.get().getPasswordHash())) {
            String reason = found.isPresent() && !found.get().isActive()
                    ? "Account is disabled"
                    : "Invalid username or password";
            auditLogService.record(request.username(), null, AUDIT_ACTION_LOGIN_FAILURE,
                    AUDIT_TARGET_TYPE_AUTH, request.username(), reason);
            throw new InvalidCredentialsException();
        }

        UserAccount user = found.get();
        user.markLogin(Instant.now());
        String accessToken = jwtTokenProvider.createToken(user.getUsername(), List.of(user.getRole().authority()));
        String refreshToken = refreshTokenService.issue(user);
        auditLogService.record(user.getUsername(), user.getRole().authority(), AUDIT_ACTION_LOGIN_SUCCESS,
                AUDIT_TARGET_TYPE_AUTH, user.getUsername(), "Login succeeded");
        return LoginResponse.bearer(
                accessToken, jwtTokenProvider.expirationSeconds(), refreshToken, UserSummary.from(user));
    }

    /** 리프레시 토큰 회전 후 새 액세스+리프레시 쌍을 반환한다.
     * noRollbackFor: 재사용 감지 시 하위 서비스가 남긴 세션 폐기·감사 기록을 보존한다. */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public LoginResponse refresh(String rawRefreshToken) {
        RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(rawRefreshToken);
        UserAccount user = rotation.user();
        String accessToken = jwtTokenProvider.createToken(user.getUsername(), List.of(user.getRole().authority()));
        return LoginResponse.bearer(
                accessToken, jwtTokenProvider.expirationSeconds(), rotation.newRefreshToken(), UserSummary.from(user));
    }

    /** 제시된 리프레시 토큰만 폐기한다(다른 브라우저 세션은 유지). */
    @Transactional
    public void logout(String rawRefreshToken, String principalName) {
        refreshTokenService.revoke(rawRefreshToken);
        if (principalName != null) {
            auditLogService.record(AUDIT_ACTION_LOGOUT, AUDIT_TARGET_TYPE_AUTH, principalName, "Logout");
        }
    }

    @Transactional(readOnly = true)
    public UserSummary me(String principalName) {
        return UserSummary.from(requireActiveUser(principalName));
    }

    /** 본인 비밀번호 변경. 현재 비밀번호 검증 후 전체 세션(리프레시 토큰)을 폐기한다. */
    @Transactional
    public void changePassword(String principalName, PasswordChangeRequest request) {
        UserAccount user = requireActiveUser(principalName);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        refreshTokenService.revokeAllForUser(user);
        auditLogService.record(AUDIT_ACTION_PASSWORD_CHANGE, AUDIT_TARGET_TYPE_AUTH, user.getUsername(),
                "Password changed by owner; all sessions revoked");
    }

    private UserAccount requireActiveUser(String principalName) {
        if (principalName == null || principalName.isBlank()) {
            throw new AuthenticationRequiredException();
        }
        return userAccountRepository.findByUsername(principalName)
                .filter(UserAccount::isActive)
                .orElseThrow(AuthenticationRequiredException::new);
    }
}
