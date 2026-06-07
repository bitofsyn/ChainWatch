package com.chainwatch.backend.analysis.repository;

import com.chainwatch.backend.analysis.domain.AiAnalysisReport;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAnalysisReportRepository extends JpaRepository<AiAnalysisReport, Long> {
    Optional<AiAnalysisReport> findByDetectionEventId(Long detectionEventId);
}
