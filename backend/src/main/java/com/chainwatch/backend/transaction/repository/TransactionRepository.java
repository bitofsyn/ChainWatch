package com.chainwatch.backend.transaction.repository;

import com.chainwatch.backend.transaction.domain.Transaction;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByTxHash(String txHash);

    @Query("""
            select count(t)
            from Transaction t
            where lower(t.fromAddress) = lower(:fromAddress)
              and t.timestamp >= :thresholdTime
            """)
    long countRecentTransfersFromAddress(
            @Param("fromAddress") String fromAddress,
            @Param("thresholdTime") Instant thresholdTime
    );
}
