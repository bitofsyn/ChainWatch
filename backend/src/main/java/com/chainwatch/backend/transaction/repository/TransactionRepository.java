package com.chainwatch.backend.transaction.repository;

import com.chainwatch.backend.transaction.domain.Transaction;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query("""
            select t
            from Transaction t
            where (:wallet is null or lower(t.fromAddress) = lower(:wallet) or lower(t.toAddress) = lower(:wallet))
              and (:blockNumber is null or t.blockNumber = :blockNumber)
              and (:from is null or t.timestamp >= :from)
              and (:to is null or t.timestamp <= :to)
            """)
    Page<Transaction> search(
            @Param("wallet") String wallet,
            @Param("blockNumber") Long blockNumber,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );
}
