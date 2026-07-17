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

    /** AI 분석 리포트가 아직 없는 고위험 이벤트를 오래된 순으로 조회 (자동 분석 워커 배치 선정) */
    @Query("""
            select e
            from DetectionEvent e
            where e.riskLevel in :levels
              and not exists (select 1 from AiAnalysisReport r where r.detectionEvent = e)
            order by e.detectedAt asc
            """)
    List<DetectionEvent> findPendingAnalysisOldestFirst(
            @Param("levels") Collection<RiskLevel> levels, Pageable pageable);

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

    /** 반열림 구간 [from, to) 탐지 건수. 탐지율 KPI(탐지/수집)의 분자에 사용한다. */
    @Query("select count(e) from DetectionEvent e where e.detectedAt >= :from and e.detectedAt < :to")
    long countDetectedInWindow(@Param("from") Instant from, @Param("to") Instant to);

    /** 대응 backlog = NEW(레거시 null 포함) + ACKNOWLEDGED. 정의는 ops overview와 통일한다. */
    @Query("""
            select count(e)
            from DetectionEvent e
            where e.status is null
               or e.status = com.chainwatch.backend.event.domain.EventStatus.NEW
               or e.status = com.chainwatch.backend.event.domain.EventStatus.ACKNOWLEDGED
            """)
    long countBacklog();

    /** backlog 중 가장 오래 대기한 이벤트의 탐지 시각. backlog가 없으면 null. */
    @Query("""
            select min(e.detectedAt)
            from DetectionEvent e
            where e.status is null
               or e.status = com.chainwatch.backend.event.domain.EventStatus.NEW
               or e.status = com.chainwatch.backend.event.domain.EventStatus.ACKNOWLEDGED
            """)
    Instant oldestBacklogDetectedAt();

    /** 위험도×처리상태 매트릭스. status null(레거시)은 서비스에서 NEW로 합산한다. */
    @Query("select e.riskLevel, e.status, count(e) from DetectionEvent e group by e.riskLevel, e.status")
    List<Object[]> countGroupByRiskLevelAndStatus();

    /** 선택 기간 기준 이벤트 유형 집계 (탐지 유형 Top N). */
    @Query("""
            select e.eventType, count(e)
            from DetectionEvent e
            where e.detectedAt >= :since
            group by e.eventType
            order by count(e) desc
            """)
    List<Object[]> countGroupByEventTypeSince(@Param("since") Instant since);

    /**
     * 시간 버킷별 탐지 건수를 DB에서 집계한다(행 로드 없이 group by).
     * 결과 행: [bucketEpochSeconds(Number), count(Number)]
     */
    @Query(value = """
            select cast(floor(extract(epoch from e.detected_at) / :bucketSeconds) as bigint) * :bucketSeconds
                       as bucket_epoch,
                   count(*) as cnt
            from detection_events e
            where e.detected_at >= :since
            group by bucket_epoch
            order by bucket_epoch
            """, nativeQuery = true)
    List<Object[]> countByTimeBucketSince(
            @Param("since") Instant since,
            @Param("bucketSeconds") long bucketSeconds
    );

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
            String assignee,
            boolean unassignedOnly,
            String network,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        return findAll(
                DetectionEventSpecifications.search(
                        eventType, riskLevel, status, wallet, assignee, unassignedOnly, network, from, to),
                pageable);
    }
}
