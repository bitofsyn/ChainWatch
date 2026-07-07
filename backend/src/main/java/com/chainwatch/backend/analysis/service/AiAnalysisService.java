package com.chainwatch.backend.analysis.service;

import com.chainwatch.backend.analysis.client.AiAnalysisClient;
import com.chainwatch.backend.analysis.client.AiAnalysisRequest;
import com.chainwatch.backend.analysis.client.AiAnalysisResult;
import com.chainwatch.backend.analysis.config.AiAnalysisProperties;
import com.chainwatch.backend.analysis.domain.AiAnalysisReport;
import com.chainwatch.backend.analysis.domain.AnalysisStatus;
import com.chainwatch.backend.analysis.repository.AiAnalysisReportRepository;
import com.chainwatch.backend.common.exception.ResourceNotFoundException;
import com.chainwatch.backend.common.metrics.ChainWatchMetrics;
import com.chainwatch.backend.event.domain.DetectionEvent;
import com.chainwatch.backend.event.repository.DetectionEventRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiAnalysisService {

    private final AiAnalysisClient aiAnalysisClient;
    private final AiAnalysisReportRepository aiAnalysisReportRepository;
    private final DetectionEventRepository detectionEventRepository;
    private final AiAnalysisProperties properties;
    private final ChainWatchMetrics metrics;

    public AiAnalysisService(
            AiAnalysisClient aiAnalysisClient,
            AiAnalysisReportRepository aiAnalysisReportRepository,
            DetectionEventRepository detectionEventRepository,
            AiAnalysisProperties properties,
            ChainWatchMetrics metrics
    ) {
        this.aiAnalysisClient = aiAnalysisClient;
        this.aiAnalysisReportRepository = aiAnalysisReportRepository;
        this.detectionEventRepository = detectionEventRepository;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Transactional
    public AiAnalysisReport analyzeEvent(Long eventId) {
        DetectionEvent event = detectionEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Detection event not found: " + eventId));

        AiAnalysisResult result = aiAnalysisClient.analyze(toRequest(event));

        return upsertReport(
                event, AnalysisStatus.COMPLETED, result.report(), result.rawResponse(), result.structuredJson());
    }

    /** 비동기 분석 요청 접수 시 PENDING 리포트를 먼저 기록해 진행 상태를 조회 가능하게 한다. */
    @Transactional
    public AiAnalysisReport markPending(Long eventId) {
        DetectionEvent event = detectionEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Detection event not found: " + eventId));

        return upsertReport(event, AnalysisStatus.PENDING, "AI 분석이 진행 중입니다.", null, null);
    }

    @Transactional
    public AiAnalysisReport markFailed(Long eventId, String reason) {
        DetectionEvent event = detectionEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Detection event not found: " + eventId));

        String message = "AI 분석 실패: " + (reason != null ? reason : "unknown error");
        return upsertReport(event, AnalysisStatus.FAILED, truncate(message, 2000), null, null);
    }

    private AiAnalysisReport upsertReport(
            DetectionEvent event,
            AnalysisStatus status,
            String report,
            String rawResponse,
            String structuredReport
    ) {
        // 컬럼 한도 초과 시 잘린 JSON을 저장하는 대신 텍스트 리포트만 유지한다.
        String storedStructuredReport =
                (structuredReport != null && structuredReport.length() > 8000) ? null : structuredReport;
        metrics.recordAiAnalysis(status.name());
        Instant analyzedAt = Instant.now();
        return aiAnalysisReportRepository.findByDetectionEventId(event.getId())
                .map(existing -> {
                    existing.update(
                            status,
                            properties.provider(),
                            properties.model(),
                            event.getSummary(),
                            report,
                            rawResponse,
                            storedStructuredReport,
                            analyzedAt
                    );
                    return aiAnalysisReportRepository.save(existing);
                })
                .orElseGet(() -> aiAnalysisReportRepository.save(new AiAnalysisReport(
                        event,
                        status,
                        properties.provider(),
                        properties.model(),
                        event.getSummary(),
                        report,
                        rawResponse,
                        storedStructuredReport,
                        analyzedAt
                )));
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    @Transactional(readOnly = true)
    public AiAnalysisReport getReport(Long eventId) {
        return aiAnalysisReportRepository.findByDetectionEventId(eventId).orElse(null);
    }

    private AiAnalysisRequest toRequest(DetectionEvent event) {
        return new AiAnalysisRequest(
                event.getId(),
                properties.provider(),
                properties.model(),
                event.getEventType().name(),
                event.getRiskLevel().name(),
                event.getRiskScore(),
                event.getWalletAddress(),
                event.getTransaction() != null ? event.getTransaction().getTxHash() : null,
                event.getSummary()
        );
    }
}
