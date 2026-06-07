package com.chainwatch.backend.event.api;

import com.chainwatch.backend.event.domain.DetectionEvent;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import java.time.Instant;

public record DetectionEventDetailResponse(
        Long id,
        EventType eventType,
        RiskLevel riskLevel,
        Integer riskScore,
        String summary,
        String walletAddress,
        String txHash,
        Instant detectedAt,
        Long transactionId
) {
    public static DetectionEventDetailResponse from(DetectionEvent event) {
        return new DetectionEventDetailResponse(
                event.getId(),
                event.getEventType(),
                event.getRiskLevel(),
                event.getRiskScore(),
                event.getSummary(),
                event.getWalletAddress(),
                event.getTransaction() != null ? event.getTransaction().getTxHash() : null,
                event.getDetectedAt(),
                event.getTransaction() != null ? event.getTransaction().getId() : null
        );
    }
}
