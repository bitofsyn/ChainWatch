package com.chainwatch.backend.analysis.domain;

import com.chainwatch.backend.event.domain.DetectionEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ai_analysis_reports")
public class AiAnalysisReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "detection_event_id", nullable = false, unique = true)
    private DetectionEvent detectionEvent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AnalysisStatus status;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(nullable = false, length = 1000)
    private String promptSummary;

    @Column(nullable = false, length = 2000)
    private String report;

    @Column(length = 4000)
    private String rawResponse;

    /** AI 서버가 반환한 구조화 분석(JSON 문자열). 비구조화 응답이면 null. */
    @Column(length = 8000)
    private String structuredReport;

    @Column(nullable = false)
    private Instant analyzedAt;

    /** LLM 호출~응답까지 실측한 처리 시간(ms). 실패/구버전 리포트는 null. */
    @Column(name = "processing_ms")
    private Long processingMs;

    protected AiAnalysisReport() {
    }

    public AiAnalysisReport(
            DetectionEvent detectionEvent,
            AnalysisStatus status,
            String provider,
            String model,
            String promptSummary,
            String report,
            String rawResponse,
            Instant analyzedAt
    ) {
        this(detectionEvent, status, provider, model, promptSummary, report, rawResponse, null, analyzedAt);
    }

    public AiAnalysisReport(
            DetectionEvent detectionEvent,
            AnalysisStatus status,
            String provider,
            String model,
            String promptSummary,
            String report,
            String rawResponse,
            String structuredReport,
            Instant analyzedAt
    ) {
        this.detectionEvent = detectionEvent;
        this.status = status;
        this.provider = provider;
        this.model = model;
        this.promptSummary = promptSummary;
        this.report = report;
        this.rawResponse = rawResponse;
        this.structuredReport = structuredReport;
        this.analyzedAt = analyzedAt;
    }

    public Long getId() {
        return id;
    }

    public DetectionEvent getDetectionEvent() {
        return detectionEvent;
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public String getPromptSummary() {
        return promptSummary;
    }

    public String getReport() {
        return report;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public String getStructuredReport() {
        return structuredReport;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }

    public Long getProcessingMs() {
        return processingMs;
    }

    public void recordProcessingMs(Long processingMs) {
        this.processingMs = processingMs;
    }

    public void update(
            AnalysisStatus status,
            String provider,
            String model,
            String promptSummary,
            String report,
            String rawResponse,
            Instant analyzedAt
    ) {
        update(status, provider, model, promptSummary, report, rawResponse, null, analyzedAt);
    }

    public void update(
            AnalysisStatus status,
            String provider,
            String model,
            String promptSummary,
            String report,
            String rawResponse,
            String structuredReport,
            Instant analyzedAt
    ) {
        this.status = status;
        this.provider = provider;
        this.model = model;
        this.promptSummary = promptSummary;
        this.report = report;
        this.rawResponse = rawResponse;
        this.structuredReport = structuredReport;
        this.analyzedAt = analyzedAt;
    }
}
