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

    long countByTimestampAfter(Instant threshold);

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

    /**
     * 특정 발신 지갑이 최근 시간창 동안 자금을 보낸 서로 다른 수신 주소의 수(out-degree).
     * 자금 분산(peeling chain/스플리팅) 그래프 패턴 탐지에 사용한다.
     * 컨트랙트 생성 등 수신자 미상 트랜잭션은 집계에서 제외한다.
     */
    @Query("""
            select count(distinct lower(t.toAddress))
            from Transaction t
            where lower(t.fromAddress) = lower(:fromAddress)
              and t.timestamp >= :thresholdTime
              and t.toAddress is not null
              and t.toAddress <> 'CONTRACT_CREATION'
            """)
    long countDistinctRecipientsFromAddress(
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
