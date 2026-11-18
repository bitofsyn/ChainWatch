package com.chainwatch.backend.notification.service;

import com.chainwatch.backend.detection.domain.DetectionEventCreated;
import com.chainwatch.backend.notification.domain.NotificationMessage;
import com.chainwatch.backend.ops.config.InfraProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Kafka 없이 단독 실행하는 구성에서 탐지 이벤트를 알림으로 넘긴다.
 *
 * <p>Kafka 구성에서는 detected-events 토픽의 컨슈머가 알림을 보낸다. 단독 실행에서는 그 토픽으로
 * 발행하지 않으므로 이 경로가 없으면 웹훅을 설정해도 알림이 한 건도 나가지 않는다.
 * 이벤트 저장이 커밋된 뒤에 보내 롤백된 이벤트로 알림이 나가는 일을 막는다.
 */
@Component
public class StandaloneNotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(StandaloneNotificationDispatcher.class);

    private final NotificationService notificationService;
    private final boolean kafkaEnabled;

    public StandaloneNotificationDispatcher(NotificationService notificationService, InfraProperties infraProperties) {
        this.notificationService = notificationService;
        this.kafkaEnabled = infraProperties.kafkaEnabled();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDetectionEventCreated(DetectionEventCreated event) {
        if (kafkaEnabled) {
            return; // Kafka 컨슈머가 담당한다. 여기서도 보내면 알림이 두 번 나간다.
        }
        try {
            notificationService.notify(NotificationMessage.from(event.message()));
        } catch (RuntimeException exception) {
            // 알림 실패가 수집·탐지 흐름을 끊지 않게 한다.
            log.warn("standalone notification failed | eventId={} error={}",
                    event.message().eventId(), exception.getMessage());
        }
    }
}
