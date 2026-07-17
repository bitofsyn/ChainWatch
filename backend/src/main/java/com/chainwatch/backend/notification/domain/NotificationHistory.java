package com.chainwatch.backend.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 채널별 알림 발송 시도 이력. 성공/실패 모두 기록해
 * 알림 유실 여부를 운영 중에 추적할 수 있게 한다.
 */
@Entity
@Table(
        name = "notification_history",
        indexes = {
                @Index(name = "idx_notification_history_sent_at", columnList = "sent_at"),
                @Index(name = "idx_notification_history_event_id", columnList = "event_id")
        }
)
public class NotificationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id")
    private Long eventId;

    @Column(length = 50)
    private String eventType;

    @Column(nullable = false)
    private Integer riskScore;

    @Column(nullable = false, length = 30)
    private String channel;

    @Column(nullable = false)
    private Boolean success;

    @Column(length = 500)
    private String errorMessage;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    /** 웹훅 발송 시도에 걸린 실측 시간(ms). 드릴/구버전 이력은 null. */
    @Column(name = "duration_ms")
    private Long durationMs;

    protected NotificationHistory() {
    }

    public NotificationHistory(
            Long eventId,
            String eventType,
            Integer riskScore,
            String channel,
            Boolean success,
            String errorMessage,
            Instant sentAt
    ) {
        this(eventId, eventType, riskScore, channel, success, errorMessage, sentAt, null);
    }

    public NotificationHistory(
            Long eventId,
            String eventType,
            Integer riskScore,
            String channel,
            Boolean success,
            String errorMessage,
            Instant sentAt,
            Long durationMs
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.riskScore = riskScore;
        this.channel = channel;
        this.success = success;
        this.errorMessage = errorMessage;
        this.sentAt = sentAt;
        this.durationMs = durationMs;
    }

    public Long getId() {
        return id;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public String getChannel() {
        return channel;
    }

    public Boolean getSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }
}
