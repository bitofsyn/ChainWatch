package com.chainwatch.backend.analysis.repository;

import com.chainwatch.backend.analysis.domain.AiAnalysisReport;
import com.chainwatch.backend.analysis.domain.AnalysisStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAnalysisReportRepository extends JpaRepository<AiAnalysisReport, Long> {
    Optional<AiAnalysisReport> findByDetectionEventId(Long detectionEventId);

    long countByStatus(AnalysisStatus status);

    long countByStatusAndAnalyzedAtAfter(AnalysisStatus status, Instant threshold);

    @EntityGraph(attributePaths = "detectionEvent")
    List<AiAnalysisReport> findTop6ByOrderByAnalyzedAtDesc();

    @EntityGraph(attributePaths = "detectionEvent")
    List<AiAnalysisReport> findTop4ByStatusOrderByAnalyzedAtDesc(AnalysisStatus status);

    /** 오래 PENDING에 머문 리포트 조회 (자동 분석 워커의 stale 재제출 대상) */
    @EntityGraph(attributePaths = "detectionEvent")
    List<AiAnalysisReport> findTop3ByStatusAndAnalyzedAtBeforeOrderByAnalyzedAtAsc(
            AnalysisStatus status, Instant threshold);
}
