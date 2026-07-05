package com.chainwatch.backend.analysis.service;

import com.chainwatch.backend.analysis.client.AiAnalysisClient;
import com.chainwatch.backend.analysis.client.AiAnalysisRequest;
import com.chainwatch.backend.analysis.client.AiAnalysisResult;
import com.chainwatch.backend.analysis.config.AiAnalysisProperties;
import com.chainwatch.backend.analysis.domain.AiAnalysisReport;
import com.chainwatch.backend.analysis.domain.AnalysisStatus;
import com.chainwatch.backend.analysis.repository.AiAnalysisReportRepository;
import com.chainwatch.backend.common.exception.ResourceNotFoundException;
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

    public AiAnalysisService(
            AiAnalysisClient aiAnalysisClient,
            AiAnalysisReportRepository aiAnalysisReportRepository,
            DetectionEventRepository detectionEventRepository,
            AiAnalysisProperties properties
    ) {
        this.aiAnalysisClient = aiAnalysisClient;
        this.aiAnalysisReportRepository = aiAnalysisReportRepository;
        this.detectionEventRepository = detectionEventRepository;
        this.properties = properties;
    }

    @Transactional
    public AiAnalysisReport analyzeEvent(Long eventId) {
        DetectionEvent event = detectionEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Detection event not found: " + eventId));

        AiAnalysisResult result = aiAnalysisClient.analyze(toRequest(event));
        Instant analyzedAt = Instant.now();

        return aiAnalysisReportRepository.findByDetectionEventId(eventId)
                .map(existing -> {
                    existing.update(
                            AnalysisStatus.COMPLETED,
                            properties.provider(),
                            properties.model(),
                            event.getSummary(),
                            result.report(),
                            result.rawResponse(),
                            analyzedAt
                    );
                    return aiAnalysisReportRepository.save(existing);
                })
                .orElseGet(() -> aiAnalysisReportRepository.save(new AiAnalysisReport(
                        event,
                        AnalysisStatus.COMPLETED,
                        properties.provider(),
                        properties.model(),
                        event.getSummary(),
                        result.report(),
                        result.rawResponse(),
                        analyzedAt
                )));
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
