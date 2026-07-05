package com.chainwatch.backend.collector.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainwatch.backend.collector.dto.BlockDto;
import com.chainwatch.backend.collector.dto.TransactionDto;
import com.chainwatch.backend.collector.metrics.CollectorMetrics;
import com.chainwatch.backend.messaging.config.KafkaTopicProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class RawEventPublisherTest {

    private static final String NETWORK = "ethereum-mainnet";

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private RawEventPublisher publisher;

    @BeforeEach
    void setUp() {
        KafkaTopicProperties topics = new KafkaTopicProperties(
                "chainwatch.raw-blocks",
                "chainwatch.raw-transactions",
                "chainwatch.detected-events"
        );
        publisher = new RawEventPublisher(kafkaTemplate, topics, new CollectorMetrics(new SimpleMeterRegistry()));
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(sendResult()));
    }

    @Test
    void publishesBlockEventKeyedByBlockNumber() {
        publisher.publishBlock(block());

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("chainwatch.raw-blocks"), eq("42"), payloadCaptor.capture());

        RawBlockEvent event = (RawBlockEvent) payloadCaptor.getValue();
        assertThat(event.blockNumber()).isEqualTo(42L);
        assertThat(event.transactionCount()).isEqualTo(1);
        assertThat(event.network()).isEqualTo(NETWORK);
        assertThat(event.collectedAt()).isNotNull();
    }

    @Test
    void publishesEachTransactionKeyedByTxHash() {
        publisher.publishBlock(block());

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("chainwatch.raw-transactions"), eq("0xtxhash"), payloadCaptor.capture());

        RawTransactionEvent event = (RawTransactionEvent) payloadCaptor.getValue();
        assertThat(event.txHash()).isEqualTo("0xtxhash");
        assertThat(event.blockNumber()).isEqualTo(42L);
        assertThat(event.valueEth()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(event.transactionType()).isEqualTo("EIP1559");
        assertThat(event.network()).isEqualTo(NETWORK);
    }

    private BlockDto block() {
        TransactionDto transaction = new TransactionDto(
                "0xtxhash", 42, "0xfrom", "0xto", BigDecimal.ONE,
                BigInteger.valueOf(21_000), BigInteger.valueOf(1_000_000_000L),
                BigInteger.valueOf(2_000_000_000L), BigInteger.valueOf(1_000_000_000L),
                BigInteger.ONE, "0x", "EIP1559", null, Instant.EPOCH, NETWORK);
        return new BlockDto(42, "0xhash", "0xparent", Instant.EPOCH, "0xminer",
                BigInteger.ZERO, BigInteger.ZERO, NETWORK, List.of(transaction));
    }

    private SendResult<String, Object> sendResult() {
        return null;
    }
}
