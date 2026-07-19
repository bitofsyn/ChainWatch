package com.chainwatch.backend.detection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainwatch.backend.agentops.service.AgentFailureRecorder;
import com.chainwatch.backend.agentops.service.AgentFaultInjector;
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

    @Mock
    private AgentFailureRecorder failureRecorder;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentFaultInjector faultInjector = new AgentFaultInjector();

    private DetectionService detectionService;

    @BeforeEach
    void setUp() {
        DetectionProperties properties = new DetectionProperties(
                DetectionProperties.DetectionTransport.SYNC,
                new BigDecimal("100.0"), null, 0, 10, 0, 15, 0, List.of(), List.of());
        detectionService = new DetectionService(
                List.of(new LargeTransferDetectionRule(properties)),
                properties,
                detectionEventRepository,
                kafkaProducer,
                new ChainWatchMetrics(new SimpleMeterRegistry()),
                objectMapper,
                faultInjector,
                failureRecorder,
                new com.chainwatch.backend.agentops.service.AgentProcessingTracker()
        );
    }

    @Test
    void recordsFailureAndSkipsRulesWhenDetectionFaultActive() {
        faultInjector.activate("detection", "test scenario", 60);

        detectionService.analyzeTransaction(transaction(new BigDecimal("250")));

        verify(failureRecorder).record(
                org.mockito.ArgumentMatchers.eq("detection"), anyString(), anyString(),
                org.mockito.ArgumentMatchers.eq(true));
        verify(detectionEventRepository, never()).save(any());
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

    /** cooldown 창 안에 같은 지갑·같은 유형 이벤트가 있으면 룰 평가(창 집계 쿼리) 자체를 생략한다. */
    @Test
    void cooldownSkipsPatternRuleEvaluationForRecentlyFlaggedWallet() {
        DetectionProperties cooldownProperties = new DetectionProperties(
                DetectionProperties.DetectionTransport.SYNC,
                new BigDecimal("100.0"), null, 3, 10, 0, 15, 30, List.of(), List.of());
        com.chainwatch.backend.transaction.repository.TransactionRepository transactionRepository =
                org.mockito.Mockito.mock(com.chainwatch.backend.transaction.repository.TransactionRepository.class);
        DetectionService service = new DetectionService(
                List.of(new com.chainwatch.backend.detection.rule.RapidTransferDetectionRule(
                        cooldownProperties, transactionRepository)),
                cooldownProperties,
                detectionEventRepository,
                kafkaProducer,
                new ChainWatchMetrics(new SimpleMeterRegistry()),
                objectMapper,
                faultInjector,
                failureRecorder,
                new com.chainwatch.backend.agentops.service.AgentProcessingTracker()
        );
        when(detectionEventRepository.existsByWalletAddressAndEventTypeAndDetectedAtAfter(
                org.mockito.ArgumentMatchers.eq("0xfrom"),
                org.mockito.ArgumentMatchers.eq(EventType.RAPID_TRANSFER),
                any()))
                .thenReturn(true);

        service.analyzeTransaction(transaction(BigDecimal.ONE));

        verify(transactionRepository, never()).countRecentTransfersFromAddress(anyString(), any());
        verify(detectionEventRepository, never()).save(any());
    }

    /** cooldown 창에 기존 이벤트가 없으면 룰이 평소대로 평가·발화한다. */
    @Test
    void cooldownAllowsRuleWhenNoRecentEventExists() {
        DetectionProperties cooldownProperties = new DetectionProperties(
                DetectionProperties.DetectionTransport.SYNC,
                new BigDecimal("100.0"), null, 3, 10, 0, 15, 30, List.of(), List.of());
        com.chainwatch.backend.transaction.repository.TransactionRepository transactionRepository =
                org.mockito.Mockito.mock(com.chainwatch.backend.transaction.repository.TransactionRepository.class);
        DetectionService service = new DetectionService(
                List.of(new com.chainwatch.backend.detection.rule.RapidTransferDetectionRule(
                        cooldownProperties, transactionRepository)),
                cooldownProperties,
                detectionEventRepository,
                kafkaProducer,
                new ChainWatchMetrics(new SimpleMeterRegistry()),
                objectMapper,
                faultInjector,
                failureRecorder,
                new com.chainwatch.backend.agentops.service.AgentProcessingTracker()
        );
        when(detectionEventRepository.existsByWalletAddressAndEventTypeAndDetectedAtAfter(
                anyString(), any(), any())).thenReturn(false);
        when(transactionRepository.countRecentTransfersFromAddress(anyString(), any())).thenReturn(5L);
        when(detectionEventRepository.existsByTransactionIdAndEventType(any(), any())).thenReturn(false);
        when(detectionEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.analyzeTransaction(transaction(BigDecimal.ONE));

        ArgumentCaptor<DetectionEvent> captor = ArgumentCaptor.forClass(DetectionEvent.class);
        verify(detectionEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(EventType.RAPID_TRANSFER);
    }

    private Transaction transaction(BigDecimal amount) {
        return new Transaction("0xtx", "0xfrom", "0xto", amount, BigDecimal.ZERO, 100L, Instant.now(), null);
    }
}
