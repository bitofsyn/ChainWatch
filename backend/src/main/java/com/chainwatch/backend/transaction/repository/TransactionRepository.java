package com.chainwatch.backend.transaction.repository;

import com.chainwatch.backend.transaction.domain.Transaction;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository
        extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByTxHash(String txHash);

    List<Transaction> findByTxHashIn(Collection<String> txHashes);

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

    default Page<Transaction> search(
            String wallet,
            Long blockNumber,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        return findAll(TransactionSpecifications.search(wallet, blockNumber, from, to), pageable);
    }
}
