package com.chainwatch.backend.analysis.api;

import com.chainwatch.backend.analysis.client.AiStructuredAnalysis;
import com.chainwatch.backend.analysis.domain.AiAnalysisReport;
import com.chainwatch.backend.analysis.domain.AnalysisStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record AiAnalysisReportResponse(
        Long id,
        AnalysisStatus status,
        String provider,
        String model,
        String promptSummary,
        String report,
        Instant analyzedAt,
        boolean structured,
        AiStructuredAnalysis structuredAnalysis
) {
    private static final Logger log = LoggerFactory.getLogger(AiAnalysisReportResponse.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static AiAnalysisReportResponse from(AiAnalysisReport report) {
        AiStructuredAnalysis structuredAnalysis = parseStructured(report.getStructuredReport());
        return new AiAnalysisReportResponse(
                report.getId(),
                report.getStatus(),
                report.getProvider(),
                report.getModel(),
                report.getPromptSummary(),
                report.getReport(),
                report.getAnalyzedAt(),
                structuredAnalysis != null,
                structuredAnalysis
        );
    }

    /** 저장된 구조화 분석 JSON을 역직렬화한다. 없거나 손상된 경우 null(텍스트 리포트만 제공). */
    private static AiStructuredAnalysis parseStructured(String structuredReport) {
        if (structuredReport == null || structuredReport.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(structuredReport, AiStructuredAnalysis.class);
        } catch (Exception exception) {
            log.warn("Failed to parse stored structured AI analysis; falling back to text-only report", exception);
            return null;
        }
    }
}
