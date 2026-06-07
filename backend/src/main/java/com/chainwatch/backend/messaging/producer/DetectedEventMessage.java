package com.chainwatch.backend.messaging.producer;

import com.chainwatch.backend.event.domain.DetectionEvent;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import java.time.Instant;

public record DetectedEventMessage(
        Long eventId,
        EventType eventType,
        RiskLevel riskLevel,
        Integer riskScore,
        String summary,
        String walletAddress,
        String txHash,
        Instant detectedAt
) {
    public static DetectedEventMessage from(DetectionEvent event) {
        return new DetectedEventMessage(
                event.getId(),
                event.getEventType(),
                event.getRiskLevel(),
                event.getRiskScore(),
                event.getSummary(),
                event.getWalletAddress(),
                event.getTransaction() != null ? event.getTransaction().getTxHash() : null,
                event.getDetectedAt()
        );
    }
}
