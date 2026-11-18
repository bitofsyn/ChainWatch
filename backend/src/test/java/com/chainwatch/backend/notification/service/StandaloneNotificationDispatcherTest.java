package com.chainwatch.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chainwatch.backend.detection.domain.DetectionEventCreated;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import com.chainwatch.backend.messaging.producer.DetectedEventMessage;
import com.chainwatch.backend.notification.domain.NotificationMessage;
import com.chainwatch.backend.ops.config.InfraProperties;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Kafka 없이 실행할 때만 탐지 이벤트를 알림으로 직접 넘긴다. */
class StandaloneNotificationDispatcherTest {

    private final NotificationService notificationService = mock(NotificationService.class);

    private static DetectionEventCreated created() {
        return new DetectionEventCreated(new DetectedEventMessage(
                7L, EventType.LARGE_TRANSFER, RiskLevel.HIGH, 85, "대규모 이체", "0xabc", "0xtx", Instant.now()));
    }

    @Test
    void sendsNotificationDirectlyWhenKafkaIsNotUsed() {
        new StandaloneNotificationDispatcher(notificationService, new InfraProperties(false, false))
                .onDetectionEventCreated(created());

        ArgumentCaptor<NotificationMessage> sent = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationService).notify(sent.capture());
        assertThat(sent.getValue().eventId()).isEqualTo(7L);
        assertThat(sent.getValue().riskScore()).isEqualTo(85);
    }

    @Test
    void leavesNotificationToKafkaConsumerWhenKafkaIsUsed() {
        new StandaloneNotificationDispatcher(notificationService, new InfraProperties(true, true))
                .onDetectionEventCreated(created());

        verify(notificationService, never()).notify(any());
    }

    @Test
    void notificationFailureDoesNotBreakCollection() {
        doThrow(new IllegalStateException("webhook down")).when(notificationService).notify(any());
        StandaloneNotificationDispatcher dispatcher =
                new StandaloneNotificationDispatcher(notificationService, new InfraProperties(false, false));

        assertThatCode(() -> dispatcher.onDetectionEventCreated(created())).doesNotThrowAnyException();
    }
}
