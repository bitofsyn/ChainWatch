package com.chainwatch.backend.collector.kafka;

import com.chainwatch.backend.collector.dto.BlockDto;
import com.chainwatch.backend.collector.dto.TransactionDto;
import com.chainwatch.backend.collector.metrics.CollectorMetrics;
import com.chainwatch.backend.messaging.config.KafkaTopicProperties;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 수집된 블록/트랜잭션을 raw-blocks, raw-transactions 토픽으로 발행한다.
 * 발행은 비동기이며 실패는 로그와 메트릭으로 기록한다(수집 파이프라인을 막지 않는다).
 */
@Component
public class RawEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RawEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties topics;
    private final CollectorMetrics metrics;

    public RawEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaTopicProperties topics,
            CollectorMetrics metrics
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
        this.metrics = metrics;
    }

    public void publishBlock(BlockDto block) {
        Instant collectedAt = Instant.now();

        try {
            RawBlockEvent blockEvent = RawBlockEvent.from(block, collectedAt);
            kafkaTemplate.send(topics.rawBlocks(), String.valueOf(block.blockNumber()), blockEvent)
                    .whenComplete((result, failure) -> {
                        if (failure != null) {
                            metrics.incrementError();
                            log.error("[ERROR] Failed to publish raw block {} to {}: {}",
                                    block.blockNumber(), topics.rawBlocks(), failure.getMessage());
                        } else {
                            metrics.recordRawBlockPublished();
                        }
                    });

            for (TransactionDto transaction : block.transactions()) {
                RawTransactionEvent transactionEvent = RawTransactionEvent.from(transaction, collectedAt);
                kafkaTemplate.send(topics.rawTransactions(), transaction.txHash(), transactionEvent)
                        .whenComplete((result, failure) -> {
                            if (failure != null) {
                                metrics.incrementError();
                                log.error("[ERROR] Failed to publish raw transaction {} to {}: {}",
                                        transaction.txHash(), topics.rawTransactions(), failure.getMessage());
                            } else {
                                metrics.recordRawTransactionsPublished(1);
                            }
                        });
            }

            log.info("[KAFKA_PUBLISH] block={} transactions={} topics=[{}, {}]",
                    block.blockNumber(), block.transactionCount(), topics.rawBlocks(), topics.rawTransactions());
        } catch (RuntimeException exception) {
            // 브로커 장애 시 send()가 동기로 실패할 수 있다(max.block.ms 대기 후).
            // 트랜잭션 수백 건에 대해 타임아웃을 반복하며 수집 루프를 세우지 않도록 즉시 중단한다.
            metrics.incrementError();
            log.error("[ERROR] Kafka publish aborted for block {}: {}", block.blockNumber(), exception.getMessage());
        }
    }
}
