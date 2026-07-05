package com.chainwatch.backend.messaging.consumer;

import com.chainwatch.backend.collector.kafka.RawTransactionEvent;
import com.chainwatch.backend.collector.util.GasFees;
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
            topics = "${chainwatch.kafka.topics.raw-transactions}",
            containerFactory = "rawTransactionFeedKafkaListenerContainerFactory"
    )
    public void consumeRawTransaction(RawTransactionEvent event) {
        feedCacheService.cacheTransaction(toFeedMessage(event));
    }

    @KafkaListener(
            topics = "${chainwatch.kafka.topics.detected-events}",
            containerFactory = "detectedEventKafkaListenerContainerFactory"
    )
    public void consumeDetectedEvent(DetectedEventMessage message) {
        feedCacheService.cacheEvent(message);
    }

    /**
     * 피드 API 응답 계약(CollectedTransactionMessage)을 유지하기 위한 매핑.
     * DB id는 피드 경로에서 조회하지 않으므로 null이다(프론트는 txHash를 key로 사용).
     */
    private CollectedTransactionMessage toFeedMessage(RawTransactionEvent event) {
        return new CollectedTransactionMessage(
                null,
                event.txHash(),
                event.fromAddress(),
                event.toAddress(),
                event.valueEth(),
                GasFees.estimateFeeEth(event.gasPriceWei(), event.maxFeePerGasWei(), event.gas()),
                event.blockNumber(),
                event.timestamp(),
                event.contractAddress()
        );
    }
}
