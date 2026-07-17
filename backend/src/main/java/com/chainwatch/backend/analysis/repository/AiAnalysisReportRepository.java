package com.chainwatch.backend.analysis.repository;

import com.chainwatch.backend.analysis.domain.AiAnalysisReport;
import com.chainwatch.backend.analysis.domain.AnalysisStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiAnalysisReportRepository extends JpaRepository<AiAnalysisReport, Long> {
    Optional<AiAnalysisReport> findByDetectionEventId(Long detectionEventId);

    long countByStatus(AnalysisStatus status);

    long countByStatusAndAnalyzedAtAfter(AnalysisStatus status, Instant threshold);

    /** 워커가 재제출할 stale PENDING 수 = 재시도 대기 중인 분석 건수. */
    long countByStatusAndAnalyzedAtBefore(AnalysisStatus status, Instant threshold);

    /** 최근 완료 리포트의 실측 처리 시간 평균(ms). 표본이 없으면 null. */
    @Query("""
            select avg(r.processingMs) from AiAnalysisReport r
            where r.status = :status and r.analyzedAt > :threshold and r.processingMs is not null
            """)
    Double averageProcessingMs(@Param("status") AnalysisStatus status, @Param("threshold") Instant threshold);

    @EntityGraph(attributePaths = "detectionEvent")
    List<AiAnalysisReport> findTop6ByOrderByAnalyzedAtDesc();

    @EntityGraph(attributePaths = "detectionEvent")
    List<AiAnalysisReport> findTop4ByStatusOrderByAnalyzedAtDesc(AnalysisStatus status);

    /** 오래 PENDING에 머문 리포트 조회 (자동 분석 워커의 stale 재제출 대상) */
    @EntityGraph(attributePaths = "detectionEvent")
    List<AiAnalysisReport> findTop3ByStatusAndAnalyzedAtBeforeOrderByAnalyzedAtAsc(
            AnalysisStatus status, Instant threshold);
}
