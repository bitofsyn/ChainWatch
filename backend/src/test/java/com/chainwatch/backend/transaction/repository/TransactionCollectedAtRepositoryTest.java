package com.chainwatch.backend.transaction.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.chainwatch.backend.transaction.domain.Transaction;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.util.ReflectionTestUtils;

/** 대시보드 처리량은 블록 시각이 아니라 수집 시각으로 센다. */
@DataJpaTest
class TransactionCollectedAtRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-11-27T12:00:00Z");

    @Autowired
    private TransactionRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void stampsCollectedAtWhenSaved() {
        Transaction saved = repository.saveAndFlush(transaction("0x01", NOW.minus(Duration.ofHours(3))));

        assertThat(saved.getCollectedAt()).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
    }

    /** 수집이 20분 밀린 상황: 블록 시각으로는 최근 5분이 0건이지만 실제로는 방금 수집했다. */
    @Test
    void countsWindowByCollectedTimeNotBlockTime() {
        Instant oldBlockTime = NOW.minus(Duration.ofMinutes(20));
        repository.save(collected("0x01", oldBlockTime, NOW.minus(Duration.ofMinutes(1))));
        repository.save(collected("0x02", oldBlockTime, NOW.minus(Duration.ofMinutes(7))));
        repository.flush();

        assertThat(repository.countCollectedInWindow(NOW.minus(Duration.ofMinutes(5)), NOW)).isEqualTo(1);
        assertThat(repository.countCollectedInWindow(
                NOW.minus(Duration.ofMinutes(10)), NOW.minus(Duration.ofMinutes(5)))).isEqualTo(1);
    }

    /** 수집 시각이 없는 예전 행은 블록 시각 버킷에 들어간다. */
    @Test
    void bucketsByCollectedTimeAndFallsBackToBlockTimeForLegacyRows() {
        repository.save(collected("0x01", NOW.minus(Duration.ofMinutes(20)), NOW.plusSeconds(30)));
        repository.save(transaction("0xlegacy", NOW.minus(Duration.ofMinutes(2))));
        repository.flush();
        entityManager.getEntityManager()
                .createNativeQuery("update transactions set collected_at = null where tx_hash = '0xlegacy'")
                .executeUpdate();
        entityManager.clear();

        List<Object[]> rows = repository.countCollectedByTimeBucketSince(NOW.minus(Duration.ofMinutes(5)), 300);

        assertThat(rows).hasSize(2);
        assertThat(((Number) rows.get(0)[0]).longValue())
                .isEqualTo(NOW.minus(Duration.ofMinutes(5)).getEpochSecond());
        assertThat(((Number) rows.get(0)[1]).longValue()).isEqualTo(1);
        assertThat(((Number) rows.get(1)[0]).longValue()).isEqualTo(NOW.getEpochSecond());
        assertThat(((Number) rows.get(1)[1]).longValue()).isEqualTo(1);
    }

    private static Transaction collected(String hash, Instant blockTime, Instant collectedAt) {
        Transaction transaction = transaction(hash, blockTime);
        ReflectionTestUtils.setField(transaction, "collectedAt", collectedAt);
        return transaction;
    }

    private static Transaction transaction(String hash, Instant blockTime) {
        return new Transaction(hash, "0xfrom", "0xto", BigDecimal.ONE, BigDecimal.ZERO, 1L, blockTime, null);
    }
}
