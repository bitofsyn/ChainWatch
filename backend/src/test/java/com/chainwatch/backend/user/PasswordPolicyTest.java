package com.chainwatch.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chainwatch.backend.user.api.PasswordResetRequest;
import com.chainwatch.backend.user.api.UserCreateRequest;
import com.chainwatch.backend.user.domain.UserRole;
import com.chainwatch.backend.user.repository.PasswordGlobalStatRepository;
import com.chainwatch.backend.user.service.PasswordChangeService;
import com.chainwatch.backend.user.service.PasswordFingerprintService;
import com.chainwatch.backend.user.service.PasswordPolicyException;
import com.chainwatch.backend.user.service.UserService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "chainwatch.security.refresh-expiration-days=17")
class PasswordPolicyTest {
    @Autowired UserService users;
    @Autowired PasswordChangeService changes;
    @Autowired PasswordFingerprintService fingerprints;
    @Autowired PasswordGlobalStatRepository stats;
    @Autowired JdbcTemplate jdbc;

    @Test
    void rejectsRecentlyUsedPasswordAfterMoreThanTenChanges() {
        long id = users.create(new UserCreateRequest("history-policy-user", UserRole.ANALYST,
                null, "initial-password-123")).user().id();
        for (int i = 0; i < 12; i++) {
            changes.changePassword(id, "new-password-" + i + "-123");
        }
        assertThatThrownBy(() -> changes.changePassword(id, "initial-password-123"))
                .isInstanceOf(PasswordPolicyException.class);
        assertThatThrownBy(() -> changes.changePassword(id, "new-password-11-123"))
                .isInstanceOf(PasswordPolicyException.class);
    }

    @Test
    void recentFiveStillApplyAfterThreeMonthsAndCountersMoveOnSuccess() {
        long id = users.create(new UserCreateRequest("aged-policy-user", UserRole.ANALYST,
                null, "aged-password-initial-123")).user().id();
        for (int i = 0; i < 6; i++) {
            changes.changePassword(id, "aged-password-" + i + "-123");
        }
        jdbc.update("UPDATE password_history SET ended_at = ? WHERE user_id = ? AND ended_at IS NOT NULL",
                Timestamp.from(Instant.now().minus(120, ChronoUnit.DAYS)), id);

        assertThatThrownBy(() -> changes.changePassword(id, "aged-password-2-123"))
                .isInstanceOf(PasswordPolicyException.class);
        String oldFingerprint = fingerprints.fingerprint("aged-password-5-123");
        String newFingerprint = fingerprints.fingerprint("aged-password-initial-123");
        changes.changePassword(id, "aged-password-initial-123");
        assertThat(stats.findById(oldFingerprint).orElseThrow().getUseCount()).isZero();
        assertThat(stats.findById(newFingerprint).orElseThrow().getUseCount()).isEqualTo(1);
    }

    @Test
    void concurrentChangesAdmitOnlyOneAtTheHundredAccountBoundary() throws Exception {
        String target = "shared-boundary-password-123";
        String fingerprint = fingerprints.fingerprint(target);
        for (int i = 0; i < 99; i++) {
            users.create(new UserCreateRequest("existing-shared-" + i, UserRole.ANALYST, null, target));
        }
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            ids.add(users.create(new UserCreateRequest("boundary-user-" + i,
                    UserRole.ANALYST, null, "initial-boundary-" + i + "-123")).user().id());
        }
        assertThat(stats.findById(fingerprint).orElseThrow().getUseCount()).isEqualTo(99);

        CountDownLatch ready = new CountDownLatch(ids.size());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(ids.size());
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (Long id : ids) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(15, TimeUnit.SECONDS)) throw new AssertionError("start timed out");
                    try {
                        users.resetPassword(id, new PasswordResetRequest(target));
                        return true;
                    } catch (PasswordPolicyException expected) {
                        return false;
                    }
                }));
            }
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int successes = 0;
            for (Future<Boolean> result : results) {
                if (result.get(60, TimeUnit.SECONDS)) successes++;
            }
            assertThat(successes).isEqualTo(1);
            assertThat(stats.findById(fingerprint).orElseThrow().getUseCount()).isEqualTo(100);
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }
}
