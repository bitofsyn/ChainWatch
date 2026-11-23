package com.chainwatch.backend.transaction.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.chainwatch.backend.transaction.domain.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@DataJpaTest
class TransactionSliceRepositoryTest {

    @Autowired
    private TransactionRepository repository;

    @Test
    void returnsPageSizePlusOneAsSliceWithoutTotalCountContract() {
        repository.save(transaction("0x01", Instant.parse("2026-01-01T00:00:00Z")));
        repository.save(transaction("0x02", Instant.parse("2026-01-02T00:00:00Z")));
        repository.save(transaction("0x03", Instant.parse("2026-01-03T00:00:00Z")));

        var first = repository.searchSlice(
                null, null, null, null,
                PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "timestamp")));
        var second = repository.searchSlice(
                null, null, null, null,
                PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "timestamp")));

        assertThat(first.getContent()).extracting(Transaction::getTxHash).containsExactly("0x03", "0x02");
        assertThat(first.hasNext()).isTrue();
        assertThat(second.getContent()).extracting(Transaction::getTxHash).containsExactly("0x01");
        assertThat(second.hasNext()).isFalse();
    }

    private static Transaction transaction(String hash, Instant timestamp) {
        return new Transaction(
                hash,
                "0xfrom",
                "0xto",
                BigDecimal.ONE,
                BigDecimal.ZERO,
                1L,
                timestamp,
                null
        );
    }
}
