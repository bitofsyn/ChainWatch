package com.chainwatch.backend.notification.consumer;

import com.chainwatch.backend.messaging.producer.DetectedEventMessage;
import com.chainwatch.backend.notification.domain.NotificationMessage;
import com.chainwatch.backend.notification.service.NotificationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "spring.kafka", name = "bootstrap-servers")
public class DetectedEventNotificationConsumer {

    /** Agent 콘솔의 컨슈머 랙 조회에서도 참조하는 그룹 ID. */
    public static final String GROUP_ID = "chainwatch-notifications";

    private final NotificationService notificationService;

    public DetectedEventNotificationConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(
            topics = "${chainwatch.kafka.topics.detected-events}",
            groupId = GROUP_ID,
            containerFactory = "detectedEventKafkaListenerContainerFactory"
    )
    public void consumeDetectedEvent(DetectedEventMessage message) {
        notificationService.notify(NotificationMessage.from(message));
    }
}
