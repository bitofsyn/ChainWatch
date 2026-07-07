package com.chainwatch.backend.notification.service;

import com.chainwatch.backend.notification.domain.NotificationHistory;
import com.chainwatch.backend.notification.domain.NotificationMessage;
import com.chainwatch.backend.notification.repository.NotificationHistoryRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 알림 발송 시도를 DB에 기록한다.
 * 이력 저장 실패가 알림 발송 흐름을 깨지 않도록 예외는 삼키고 로그만 남긴다.
 */
@Component
public class NotificationHistoryRecorder {

    private static final Logger log = LoggerFactory.getLogger(NotificationHistoryRecorder.class);
    private static final int MAX_ERROR_LENGTH = 500;

    private final NotificationHistoryRepository repository;

    public NotificationHistoryRecorder(NotificationHistoryRepository repository) {
        this.repository = repository;
    }

    public void record(NotificationMessage message, String channel, boolean success, String errorMessage) {
        try {
            repository.save(new NotificationHistory(
                    message.eventId(),
                    message.eventType(),
                    message.riskScore(),
                    channel,
                    success,
                    truncate(errorMessage),
                    Instant.now()
            ));
        } catch (Exception exception) {
            log.warn("failed to record notification history | eventId={} channel={} error={}",
                    message.eventId(), channel, exception.getMessage());
        }
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_LENGTH);
    }
}
