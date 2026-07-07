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
}
