package com.chainwatch.backend.detection.domain;

import com.chainwatch.backend.messaging.producer.DetectedEventMessage;

/**
 * 탐지 이벤트가 새로 저장됐음을 알리는 애플리케이션 이벤트.
 * Kafka를 쓰지 않는 구성에서 알림 같은 후속 처리를 같은 프로세스 안에서 이어가기 위해 발행한다.
 */
public record DetectionEventCreated(DetectedEventMessage message) {
}
