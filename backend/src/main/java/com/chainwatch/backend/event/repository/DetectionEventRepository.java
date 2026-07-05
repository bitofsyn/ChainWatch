package com.chainwatch.backend.event.repository;

import com.chainwatch.backend.event.domain.DetectionEvent;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DetectionEventRepository
        extends JpaRepository<DetectionEvent, Long>, JpaSpecificationExecutor<DetectionEvent> {

    boolean existsByTransactionIdAndEventType(Long transactionId, EventType eventType);

    long countByDetectedAtAfter(Instant threshold);

    @Query("select e.riskLevel, count(e) from DetectionEvent e group by e.riskLevel")
    List<Object[]> countGroupByRiskLevel();

    @Query("select e.eventType, count(e) from DetectionEvent e group by e.eventType")
    List<Object[]> countGroupByEventType();

    @Query("select e.status, count(e) from DetectionEvent e group by e.status")
    List<Object[]> countGroupByStatus();

    @Query("""
            select e.walletAddress, count(e), max(e.riskScore), max(e.detectedAt)
            from DetectionEvent e
            group by e.walletAddress
            order by count(e) desc
            """)
    List<Object[]> findTopWalletsByEventCount(Pageable pageable);

    @Query("""
            select count(e), max(e.riskScore), min(e.detectedAt), max(e.detectedAt)
            from DetectionEvent e
            where lower(e.walletAddress) = lower(:wallet)
            """)
    List<Object[]> summarizeWallet(@Param("wallet") String wallet);

    @Query("""
            select e.eventType, count(e)
            from DetectionEvent e
            where lower(e.walletAddress) = lower(:wallet)
            group by e.eventType
            """)
    List<Object[]> countGroupByEventTypeForWallet(@Param("wallet") String wallet);

    @Query("""
            select e.riskLevel, count(e)
            from DetectionEvent e
            where lower(e.walletAddress) = lower(:wallet)
            group by e.riskLevel
            """)
    List<Object[]> countGroupByRiskLevelForWallet(@Param("wallet") String wallet);

    @Override
    @EntityGraph(attributePaths = "transaction")
    Optional<DetectionEvent> findById(Long id);

    @Override
    @EntityGraph(attributePaths = "transaction")
    Page<DetectionEvent> findAll(Specification<DetectionEvent> spec, Pageable pageable);

    default Page<DetectionEvent> search(
            EventType eventType,
            RiskLevel riskLevel,
            String wallet,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        return findAll(DetectionEventSpecifications.search(eventType, riskLevel, wallet, from, to), pageable);
    }
}
