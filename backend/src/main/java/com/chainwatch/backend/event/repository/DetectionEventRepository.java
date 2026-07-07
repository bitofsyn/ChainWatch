package com.chainwatch.backend.event.repository;

import com.chainwatch.backend.event.domain.DetectionEvent;
import com.chainwatch.backend.event.domain.EventStatus;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import java.time.Instant;
import java.util.Collection;
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

    List<DetectionEvent> findTop6ByOrderByDetectedAtDesc();

    List<DetectionEvent> findTop6ByStatusInOrderByDetectedAtDesc(Collection<EventStatus> statuses);

    /** AI 분석 리포트가 아직 없는 고위험 이벤트 수 (Analysis 팀 대기 큐) */
    @Query("""
            select count(e)
            from DetectionEvent e
            where e.riskLevel in :levels
              and not exists (select 1 from AiAnalysisReport r where r.detectionEvent = e)
            """)
    long countPendingAnalysis(@Param("levels") Collection<RiskLevel> levels);

    /** AI 분석 대기 중 가장 오래된 이벤트의 탐지 시각 */
    @Query("""
            select min(e.detectedAt)
            from DetectionEvent e
            where e.riskLevel in :levels
              and not exists (select 1 from AiAnalysisReport r where r.detectionEvent = e)
            """)
    Instant oldestPendingAnalysisDetectedAt(@Param("levels") Collection<RiskLevel> levels);

    /** 미처리(NEW/null) 이벤트 중 가장 오래된 탐지 시각 (Triage 팀 대기 큐) */
    @Query("""
            select min(e.detectedAt)
            from DetectionEvent e
            where e.status is null or e.status = com.chainwatch.backend.event.domain.EventStatus.NEW
            """)
    Instant oldestUnresolvedDetectedAt();

    /** 시간대별 추이 집계용. 버킷팅은 DB 방언 의존을 피해 애플리케이션에서 수행한다. */
    @Query("select e.detectedAt from DetectionEvent e where e.detectedAt >= :since")
    List<Instant> findDetectedAtSince(@Param("since") Instant since);

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
            EventStatus status,
            String wallet,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        return findAll(DetectionEventSpecifications.search(eventType, riskLevel, status, wallet, from, to), pageable);
    }
}
