package com.chainwatch.backend.messaging.consumer;

import com.chainwatch.backend.feed.service.FeedCacheService;
import com.chainwatch.backend.messaging.producer.CollectedTransactionMessage;
import com.chainwatch.backend.messaging.producer.DetectedEventMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "spring.kafka", name = "bootstrap-servers")
public class FeedCacheConsumer {

    private final FeedCacheService feedCacheService;

    public FeedCacheConsumer(FeedCacheService feedCacheService) {
        this.feedCacheService = feedCacheService;
    }

    @KafkaListener(
            topics = "${chainwatch.kafka.topics.collected-transactions}",
            containerFactory = "collectedTransactionKafkaListenerContainerFactory"
    )
    public void consumeCollectedTransaction(CollectedTransactionMessage message) {
        feedCacheService.cacheTransaction(message);
    }

    @KafkaListener(
            topics = "${chainwatch.kafka.topics.detected-events}",
            containerFactory = "detectedEventKafkaListenerContainerFactory"
    )
    public void consumeDetectedEvent(DetectedEventMessage message) {
        feedCacheService.cacheEvent(message);
    }
}
