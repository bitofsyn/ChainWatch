package com.chainwatch.backend.event.api;

import com.chainwatch.backend.analysis.api.AiAnalysisReportResponse;
import com.chainwatch.backend.event.domain.DetectionEvent;
import com.chainwatch.backend.event.domain.EventStatus;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record DetectionEventDetailResponse(
        Long id,
        EventType eventType,
        RiskLevel riskLevel,
        Integer riskScore,
        String summary,
        String walletAddress,
        String txHash,
        Instant detectedAt,
        Long transactionId,
        EventStatus status,
        String assignee,
        Instant statusChangedAt,
        String resolutionReason,
        String falsePositiveReason,
        String notes,
        String ruleVersion,
        JsonNode evidence,
        AiAnalysisReportResponse aiReport
) {
    private static final Logger log = LoggerFactory.getLogger(DetectionEventDetailResponse.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static DetectionEventDetailResponse from(DetectionEvent event, AiAnalysisReportResponse aiReport) {
        return new DetectionEventDetailResponse(
                event.getId(),
                event.getEventType(),
                event.getRiskLevel(),
                event.getRiskScore(),
                event.getSummary(),
                event.getWalletAddress(),
                event.getTransaction() != null ? event.getTransaction().getTxHash() : null,
                event.getDetectedAt(),
                event.getTransaction() != null ? event.getTransaction().getId() : null,
                event.getStatus(),
                event.getAssignee(),
                event.getStatusChangedAt(),
                event.getResolutionReason(),
                event.getFalsePositiveReason(),
                event.getNotes(),
                event.getRuleVersion(),
                parseEvidence(event.getEvidence()),
                aiReport
        );
    }

    /** 저장된 evidence JSON을 객체로 역직렬화한다. 없거나 손상된 경우 null (레거시 이벤트 포함). */
    private static JsonNode parseEvidence(String evidenceJson) {
        if (evidenceJson == null || evidenceJson.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(evidenceJson);
        } catch (Exception exception) {
            log.warn("Failed to parse stored detection evidence; returning event without evidence", exception);
            return null;
        }
    }
}
