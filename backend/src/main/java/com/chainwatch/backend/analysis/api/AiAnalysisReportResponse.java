package com.chainwatch.backend.analysis.api;

import com.chainwatch.backend.analysis.domain.AiAnalysisReport;
import com.chainwatch.backend.analysis.domain.AnalysisStatus;
import java.time.Instant;

public record AiAnalysisReportResponse(
        Long id,
        AnalysisStatus status,
        String provider,
        String model,
        String promptSummary,
        String report,
        Instant analyzedAt
) {
    public static AiAnalysisReportResponse from(AiAnalysisReport report) {
        return new AiAnalysisReportResponse(
                report.getId(),
                report.getStatus(),
                report.getProvider(),
                report.getModel(),
                report.getPromptSummary(),
                report.getReport(),
                report.getAnalyzedAt()
        );
    }
}
