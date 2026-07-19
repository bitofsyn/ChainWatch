package com.chainwatch.backend.analysis.service;

import com.chainwatch.backend.agentops.service.AgentFaultInjector;
import com.chainwatch.backend.analysis.client.AiAnalysisClient;
import com.chainwatch.backend.analysis.client.AiAnalysisRequest;
import com.chainwatch.backend.analysis.client.AiAnalysisResult;
import com.chainwatch.backend.analysis.config.AiAnalysisProperties;
import com.chainwatch.backend.analysis.domain.AiAnalysisReport;
import com.chainwatch.backend.analysis.domain.AnalysisStatus;
import com.chainwatch.backend.analysis.exception.AiAnalysisException;
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
    private final AgentFaultInjector faultInjector;

    public AiAnalysisService(
            AiAnalysisClient aiAnalysisClient,
            AiAnalysisReportRepository aiAnalysisReportRepository,
            DetectionEventRepository detectionEventRepository,
            AiAnalysisProperties properties,
            ChainWatchMetrics metrics,
            AgentFaultInjector faultInjector
    ) {
        this.aiAnalysisClient = aiAnalysisClient;
        this.aiAnalysisReportRepository = aiAnalysisReportRepository;
        this.detectionEventRepository = detectionEventRepository;
        this.properties = properties;
        this.metrics = metrics;
        this.faultInjector = faultInjector;
    }

    @Transactional
    public AiAnalysisReport analyzeEvent(Long eventId) {
        DetectionEvent event = detectionEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Detection event not found: " + eventId));

        // 장애 주입 활성 시 실제 실패 경로(호출부 catch → markFailed → FAILED 리포트)를 그대로 태운다.
        if (faultInjector.isActive("analysis")) {
            throw new AiAnalysisException("장애 주입 활성 — 시뮬레이션된 AI 분석 실패 (LLM 호출 타임아웃)");
        }

        long startedNanos = System.nanoTime();
        AiAnalysisResult result = aiAnalysisClient.analyze(toRequest(event));
        long processingMs = Math.max(1, (System.nanoTime() - startedNanos) / 1_000_000);

        // provider/model은 AI 서버가 실제 사용한 값을 우선 기록한다(폴백 발생 시에도 정확).
        AiAnalysisReport report = upsertReport(
                event,
                AnalysisStatus.COMPLETED,
                result.report(),
                result.rawResponse(),
                result.structuredJson(),
                orDefault(result.provider(), properties.provider()),
                orDefault(result.model(), properties.model())
        );
        report.recordProcessingMs(processingMs);
        return report;
    }

    /** 비동기 분석 요청 접수 시 PENDING 리포트를 먼저 기록해 진행 상태를 조회 가능하게 한다. */
    @Transactional
    public AiAnalysisReport markPending(Long eventId) {
        DetectionEvent event = detectionEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Detection event not found: " + eventId));

        AiAnalysisReport report = upsertReport(event, AnalysisStatus.PENDING, "AI 분석이 진행 중입니다.", null, null,
                properties.provider(), properties.model());
        report.recordProcessingMs(null);
        return report;
    }

    @Transactional
    public AiAnalysisReport markFailed(Long eventId, String reason) {
        DetectionEvent event = detectionEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Detection event not found: " + eventId));

        String message = "AI 분석 실패: " + (reason != null ? reason : "unknown error");
        AiAnalysisReport report = upsertReport(event, AnalysisStatus.FAILED, truncate(message, 2000), null, null,
                properties.provider(), properties.model());
        report.recordProcessingMs(null);
        return report;
    }

    private AiAnalysisReport upsertReport(
            DetectionEvent event,
            AnalysisStatus status,
            String report,
            String rawResponse,
            String structuredReport,
            String provider,
            String model
    ) {
        // 컬럼 한도 초과 시 잘린 JSON을 저장하는 대신 텍스트 리포트만 유지한다.
        String storedStructuredReport =
                (structuredReport != null && structuredReport.length() > 8000) ? null : structuredReport;
        // LLM 응답 길이는 통제 밖이므로 텍스트 필드는 컬럼 한도로 잘라 저장 실패를 막는다.
        String storedPromptSummary = truncate(event.getSummary(), 1000);
        String storedReport = report != null ? truncate(report, 2000) : null;
        String storedRawResponse = rawResponse != null ? truncate(rawResponse, 4000) : null;
        metrics.recordAiAnalysis(status.name());
        Instant analyzedAt = Instant.now();
        return aiAnalysisReportRepository.findByDetectionEventId(event.getId())
                .map(existing -> {
                    existing.update(
                            status,
                            provider,
                            model,
                            storedPromptSummary,
                            storedReport,
                            storedRawResponse,
                            storedStructuredReport,
                            analyzedAt
                    );
                    return aiAnalysisReportRepository.save(existing);
                })
                .orElseGet(() -> aiAnalysisReportRepository.save(new AiAnalysisReport(
                        event,
                        status,
                        provider,
                        model,
                        storedPromptSummary,
                        storedReport,
                        storedRawResponse,
                        storedStructuredReport,
                        analyzedAt
                )));
    }

    private static String orDefault(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
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
