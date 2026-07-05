package com.chainwatch.backend.detection.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.chainwatch.backend.collector.kafka.RawTransactionEvent;
import com.chainwatch.backend.event.domain.DetectionEvent;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.repository.DetectionEventRepository;
import com.chainwatch.backend.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

/**
 * raw-transactions 발행 → Kafka consumer 소비 → 트랜잭션 저장 → 룰 탐지 → 이벤트 저장까지의
 * 이벤트 기반 탐지 파이프라인 E2E 검증 (EmbeddedKafka).
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "chainwatch.detection.mode=kafka",
        "chainwatch.collector.enabled=false"
})
@EmbeddedKafka(partitions = 1)
class RawTransactionDetectionPipelineTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private DetectionEventRepository detectionEventRepository;

    @Test
    void consumesRawTransactionAndCreatesDetectionEvent() throws Exception {
        String txHash = "0x" + UUID.randomUUID().toString().replace("-", "");
        String receiverWallet = "0xreceiver-" + UUID.randomUUID();
        RawTransactionEvent event = new RawTransactionEvent(
                txHash, 12_345L, "0xwhale", receiverWallet, new BigDecimal("250"),
                BigInteger.valueOf(21_000), BigInteger.valueOf(1_000_000_000L), null, null,
                BigInteger.ONE, "0x", "LEGACY", null, Instant.now(), "ethereum-mainnet", Instant.now());

        kafkaTemplate.send("chainwatch.raw-transactions", txHash, event).get();

        waitUntil(() -> transactionRepository.findByTxHash(txHash).isPresent());
        waitUntil(() -> hasLargeTransferEvent(receiverWallet));

        assertThat(transactionRepository.findByTxHash(txHash))
                .hasValueSatisfying(transaction ->
                        assertThat(transaction.getAmount()).isEqualByComparingTo(new BigDecimal("250")));
    }

    private boolean hasLargeTransferEvent(String receiverWallet) {
        List<DetectionEvent> events = detectionEventRepository.findAll();
        return events.stream().anyMatch(event ->
                event.getEventType() == EventType.LARGE_TRANSFER
                        && receiverWallet.equals(event.getWalletAddress()));
    }

    private void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Condition not met within timeout");
            }
            Thread.sleep(200);
        }
    }
}
