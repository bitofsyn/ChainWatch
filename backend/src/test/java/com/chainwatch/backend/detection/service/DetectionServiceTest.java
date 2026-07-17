package com.chainwatch.backend.detection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainwatch.backend.common.metrics.ChainWatchMetrics;
import com.chainwatch.backend.detection.config.DetectionProperties;
import com.chainwatch.backend.detection.rule.LargeTransferDetectionRule;
import com.chainwatch.backend.event.domain.DetectionEvent;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.repository.DetectionEventRepository;
import com.chainwatch.backend.messaging.producer.ChainWatchKafkaProducer;
import com.chainwatch.backend.transaction.domain.Transaction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DetectionServiceTest {

    @Mock
    private DetectionEventRepository detectionEventRepository;

    @Mock
    private ChainWatchKafkaProducer kafkaProducer;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private DetectionService detectionService;

    @BeforeEach
    void setUp() {
        DetectionProperties properties = new DetectionProperties(
                DetectionProperties.DetectionTransport.SYNC,
                new BigDecimal("100.0"), null, 0, 10, 0, 15, List.of(), List.of());
        detectionService = new DetectionService(
                List.of(new LargeTransferDetectionRule(properties)),
                detectionEventRepository,
                kafkaProducer,
                new ChainWatchMetrics(new SimpleMeterRegistry()),
                objectMapper
        );
    }

    @Test
    void persistsRuleEvidenceJsonOnDetectionEvent() throws Exception {
        when(detectionEventRepository.existsByTransactionIdAndEventType(any(), any())).thenReturn(false);
        when(detectionEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        detectionService.analyzeTransaction(transaction(new BigDecimal("250")));

        ArgumentCaptor<DetectionEvent> captor = ArgumentCaptor.forClass(DetectionEvent.class);
        verify(detectionEventRepository).save(captor.capture());
        DetectionEvent event = captor.getValue();
        assertThat(event.getRuleVersion()).isEqualTo("1.0");
        assertThat(event.getEvidence()).isNotBlank();

        JsonNode evidence = objectMapper.readTree(event.getEvidence());
        assertThat(evidence.get("rule").asText()).isEqualTo("large-transfer");
        assertThat(evidence.get("ruleVersion").asText()).isEqualTo("1.0");
        assertThat(evidence.get("thresholdEth").decimalValue()).isEqualByComparingTo(new BigDecimal("100.0"));
        assertThat(evidence.get("observedAmountEth").decimalValue()).isEqualByComparingTo(new BigDecimal("250"));
        verify(kafkaProducer).publishDetectedEvent(any(), anyString());
    }

    /** 재전달·중복 소비 시 exists 체크로 수렴해 이벤트 저장/발행이 반복되지 않는다 (멱등성). */
    @Test
    void duplicateDeliveryDoesNotSaveOrPublishAgain() {
        when(detectionEventRepository.existsByTransactionIdAndEventType(any(), any())).thenReturn(true);

        detectionService.analyzeTransaction(transaction(new BigDecimal("250")));

        verify(detectionEventRepository, never()).save(any());
        verify(kafkaProducer, never()).publishDetectedEvent(any(), anyString());
    }

    @Test
    void belowThresholdTransactionCreatesNoEvent() {
        detectionService.analyzeTransaction(transaction(BigDecimal.ONE));

        verify(detectionEventRepository, never()).existsByTransactionIdAndEventType(any(), any());
        verify(detectionEventRepository, never()).save(any());
    }

    @Test
    void eventTypeMatchesRuleThatFired() {
        when(detectionEventRepository.existsByTransactionIdAndEventType(any(), any())).thenReturn(false);
        when(detectionEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        detectionService.analyzeTransaction(transaction(new BigDecimal("250")));

        ArgumentCaptor<DetectionEvent> captor = ArgumentCaptor.forClass(DetectionEvent.class);
        verify(detectionEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(EventType.LARGE_TRANSFER);
    }

    private Transaction transaction(BigDecimal amount) {
        return new Transaction("0xtx", "0xfrom", "0xto", amount, BigDecimal.ZERO, 100L, Instant.now(), null);
    }
}
