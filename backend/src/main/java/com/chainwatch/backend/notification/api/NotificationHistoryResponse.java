package com.chainwatch.backend.notification.api;

import com.chainwatch.backend.notification.domain.NotificationHistory;
import java.time.Instant;

public record NotificationHistoryResponse(
        Long id,
        Long eventId,
        String eventType,
        Integer riskScore,
        String channel,
        Boolean success,
        String errorMessage,
        Instant sentAt
) {
    public static NotificationHistoryResponse from(NotificationHistory history) {
        return new NotificationHistoryResponse(
                history.getId(),
                history.getEventId(),
                history.getEventType(),
                history.getRiskScore(),
                history.getChannel(),
                history.getSuccess(),
                history.getErrorMessage(),
                history.getSentAt()
        );
    }
}
