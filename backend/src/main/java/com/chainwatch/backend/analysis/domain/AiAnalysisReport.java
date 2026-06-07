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

    @Column(nullable = false)
    private Instant analyzedAt;

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
        this.detectionEvent = detectionEvent;
        this.status = status;
        this.provider = provider;
        this.model = model;
        this.promptSummary = promptSummary;
        this.report = report;
        this.rawResponse = rawResponse;
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

    public Instant getAnalyzedAt() {
        return analyzedAt;
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
        this.status = status;
        this.provider = provider;
        this.model = model;
        this.promptSummary = promptSummary;
        this.report = report;
        this.rawResponse = rawResponse;
        this.analyzedAt = analyzedAt;
    }
}
