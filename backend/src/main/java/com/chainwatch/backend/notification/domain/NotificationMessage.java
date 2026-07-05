package com.chainwatch.backend.notification.domain;

import com.chainwatch.backend.messaging.producer.DetectedEventMessage;
import java.time.Instant;

public record NotificationMessage(
        Long eventId,
        String eventType,
        String riskLevel,
        int riskScore,
        String summary,
        String walletAddress,
        String txHash,
        Instant detectedAt
) {
    public static NotificationMessage from(DetectedEventMessage message) {
        return new NotificationMessage(
                message.eventId(),
                message.eventType() != null ? message.eventType().name() : null,
                message.riskLevel() != null ? message.riskLevel().name() : null,
                message.riskScore() != null ? message.riskScore() : 0,
                message.summary(),
                message.walletAddress(),
                message.txHash(),
                message.detectedAt()
        );
    }

    /** Key used to suppress duplicate alerts for the same event. */
    public String dedupKey() {
        return eventId != null ? "event:" + eventId : "tx:" + txHash + ":" + eventType;
    }
}
