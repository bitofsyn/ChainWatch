package com.chainwatch.backend.user.service;

import com.chainwatch.backend.common.exception.ResourceNotFoundException;
import com.chainwatch.backend.user.domain.PasswordHistory;
import com.chainwatch.backend.user.domain.UserAccount;
import com.chainwatch.backend.user.repository.PasswordGlobalStatRepository;
import com.chainwatch.backend.user.repository.PasswordHistoryRepository;
import com.chainwatch.backend.user.repository.UserAccountRepository;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 계정별 이력과 전역 사용량을 같은 DB 트랜잭션에서 갱신한다. */
@Service
public class PasswordChangeService {
    private static final int PAGE_SIZE = 10;

    private final UserAccountRepository users;
    private final PasswordHistoryRepository history;
    private final PasswordGlobalStatRepository stats;
    private final PasswordFingerprintService fingerprints;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;

    public PasswordChangeService(UserAccountRepository users, PasswordHistoryRepository history,
            PasswordGlobalStatRepository stats, PasswordFingerprintService fingerprints,
            PasswordEncoder encoder, JdbcTemplate jdbc) {
        this.users = users;
        this.history = history;
        this.stats = stats;
        this.fingerprints = fingerprints;
        this.encoder = encoder;
        this.jdbc = jdbc;
    }

    /** 최초 발급도 전역 한도와 이력 등록을 거친다. */
    @Transactional
    public UserAccount create(String username, String rawPassword,
            com.chainwatch.backend.user.domain.UserRole role, String displayName) {
        String fingerprint = fingerprints.fingerprint(rawPassword);
        reserve(fingerprint);
        UserAccount user = new UserAccount(username, encoder.encode(rawPassword), role, displayName);
        user.setCurrentPasswordFingerprint(fingerprint);
        users.saveAndFlush(user);
        history.save(new PasswordHistory(user.getId(), user.getPasswordHash(), Instant.now()));
        return user;
    }

    @Transactional
    public UserAccount changePassword(Long userId, String rawPassword) {
        UserAccount user = users.findLockedById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다: id=" + userId));
        String fingerprint = fingerprints.fingerprint(rawPassword);
        Instant now = Instant.now();
        Instant cutoff = now.atZone(ZoneOffset.UTC).minusMonths(3).toInstant();

        if (encoder.matches(rawPassword, user.getPasswordHash())) {
            throw new PasswordPolicyException("현재 사용 중인 비밀번호로 변경할 수 없습니다.");
        }
        int inspected = 0;
        for (int page = 0; ; page++) {
            List<PasswordHistory> batch = history.findRecentEnded(userId, PageRequest.of(page, PAGE_SIZE));
            for (PasswordHistory item : batch) {
                // 현재 비밀번호도 최근 5개에 포함하므로 종료된 이력에서는 4개를 본다.
                if (inspected < 4 || !item.getEndedAt().isBefore(cutoff)) {
                    if (encoder.matches(rawPassword, item.getPasswordHash())) {
                        throw new PasswordPolicyException("최근 5개 또는 최근 3개월 이내 사용한 비밀번호입니다.");
                    }
                }
                inspected++;
                if (inspected >= 4 && item.getEndedAt().isBefore(cutoff)) {
                    batch = List.of();
                    break;
                }
            }
            if (batch.size() < PAGE_SIZE) {
                break;
            }
        }

        reserve(fingerprint);
        String previous = user.getCurrentPasswordFingerprint();
        int ended = history.endCurrent(userId, now);
        if (ended == 0) {
            // 기존 BCrypt 계정은 과거 지문을 역산할 수 없다. 첫 변경 시 이전 해시만 이력에 남긴다.
            history.save(new PasswordHistory(userId, user.getPasswordHash(), user.getCreatedAt(), now));
        } else if (ended != 1) {
            throw new IllegalStateException("Multiple active password history rows for user " + userId);
        }
        user.changePassword(encoder.encode(rawPassword), fingerprint);
        history.save(new PasswordHistory(userId, user.getPasswordHash(), now));
        if (previous != null && stats.decrement(previous) != 1) {
            throw new IllegalStateException("Missing password statistic for old fingerprint");
        }
        return user;
    }

    /** 기존 계정이 정상 인증했을 때 현재 암호만 지문 통계에 편입한다. */
    @Transactional
    public void enrollLegacy(Long userId, String rawPassword) {
        UserAccount user = users.findLockedById(userId).orElseThrow();
        if (user.getCurrentPasswordFingerprint() != null) {
            return;
        }
        if (!encoder.matches(rawPassword, user.getPasswordHash())) {
            return;
        }
        String fingerprint = fingerprints.fingerprint(rawPassword);
        ensureStatRow(fingerprint);
        if (stats.incrementLegacy(fingerprint) != 1) {
            throw new IllegalStateException("Missing password statistic for legacy enrollment");
        }
        user.setCurrentPasswordFingerprint(fingerprint);
        history.save(new PasswordHistory(userId, user.getPasswordHash(), user.getCreatedAt()));
    }

    private void reserve(String fingerprint) {
        ensureStatRow(fingerprint);
        if (stats.incrementIfBelowLimit(fingerprint) == 0) {
            throw new PasswordPolicyException("이 비밀번호는 이미 100개 이상의 계정에서 사용 중입니다.");
        }
    }

    /** 한 지문 행의 최초 생성만 DB upsert로 처리한다. 이후 증가는 조건부 UPDATE다. */
    private void ensureStatRow(String fingerprint) {
        String product = jdbc.execute((ConnectionCallback<String>) connection -> {
            try {
                DatabaseMetaData metadata = connection.getMetaData();
                return metadata.getDatabaseProductName();
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        });
        if (product != null && product.contains("PostgreSQL")) {
            jdbc.update("INSERT INTO password_global_stats (fingerprint, use_count) VALUES (?, 0) "
                    + "ON CONFLICT (fingerprint) DO NOTHING", fingerprint);
        } else if (product != null && product.contains("H2")) {
            jdbc.update("MERGE INTO password_global_stats s USING (SELECT CAST(? AS VARCHAR(64)) AS fingerprint) v "
                    + "ON (s.fingerprint = v.fingerprint) "
                    + "WHEN NOT MATCHED THEN INSERT (fingerprint, use_count) VALUES (v.fingerprint, 0)", fingerprint);
        } else {
            throw new IllegalStateException("Unsupported database for password stat upsert: " + product);
        }
    }
}
