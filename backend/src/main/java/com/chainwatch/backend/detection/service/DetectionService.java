package com.chainwatch.backend.detection.service;

import com.chainwatch.backend.agentops.service.AgentFailureRecorder;
import com.chainwatch.backend.agentops.service.AgentFaultInjector;
import com.chainwatch.backend.agentops.service.AgentProcessingTracker;
import com.chainwatch.backend.common.metrics.ChainWatchMetrics;
import com.chainwatch.backend.detection.config.DetectionProperties;
import com.chainwatch.backend.detection.domain.DetectionCommand;
import com.chainwatch.backend.detection.rule.DetectionRule;
import com.chainwatch.backend.event.domain.DetectionEvent;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.repository.DetectionEventRepository;
import com.chainwatch.backend.messaging.producer.ChainWatchKafkaProducer;
import com.chainwatch.backend.messaging.producer.DetectedEventMessage;
import com.chainwatch.backend.transaction.domain.Transaction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 중복 저장 방지는 2중으로 동작한다.
 * 1) 애플리케이션 레벨 exists 체크: 일반 경로에서 중복 저장을 걸러낸다.
 * 2) detection_events (transaction_id, event_type) DB 유니크 제약: 동시 처리/멀티 인스턴스/
 *    Kafka 재소비 경합에서 exists 체크를 동시에 통과해도 한쪽 커밋만 성공한다.
 *    실패한 쪽은 트랜잭션 롤백 후 재처리 시 exists 체크로 수렴하므로 중복 이벤트가 남지 않는다.
 *    (KAFKA 모드에서는 DefaultErrorHandler의 재시도가 재처리를 담당한다.)
 */
@Service
public class DetectionService {

    private static final Logger log = LoggerFactory.getLogger(DetectionService.class);

    private final List<DetectionRule> detectionRules;
    private final DetectionProperties detectionProperties;
    private final DetectionEventRepository detectionEventRepository;
    private final ChainWatchKafkaProducer kafkaProducer;
    private final ChainWatchMetrics metrics;
    private final ObjectMapper objectMapper;
    private final AgentFaultInjector faultInjector;
    private final AgentFailureRecorder failureRecorder;
    private final AgentProcessingTracker processingTracker;

    public DetectionService(
            List<DetectionRule> detectionRules,
            DetectionProperties detectionProperties,
            DetectionEventRepository detectionEventRepository,
            ChainWatchKafkaProducer kafkaProducer,
            ChainWatchMetrics metrics,
            ObjectMapper objectMapper,
            AgentFaultInjector faultInjector,
            AgentFailureRecorder failureRecorder,
            AgentProcessingTracker processingTracker
    ) {
        this.detectionRules = detectionRules;
        this.detectionProperties = detectionProperties;
        this.detectionEventRepository = detectionEventRepository;
        this.kafkaProducer = kafkaProducer;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.faultInjector = faultInjector;
        this.failureRecorder = failureRecorder;
        this.processingTracker = processingTracker;
    }

    @Transactional
    public void analyzeTransactions(List<Transaction> transactions) {
        for (Transaction transaction : transactions) {
            analyzeTransaction(transaction);
        }
    }

    @Transactional
    public void analyzeTransaction(Transaction transaction) {
        if (faultInjector.isActive("detection")) {
            failureRecorder.record("detection",
                    "트랜잭션 " + shortHash(transaction.getTxHash()) + " 스크리닝 실패",
                    "장애 주입 활성 — 룰 평가가 강제 실패 처리됨", true);
            return;
        }
        long startedNanos = System.nanoTime();
        for (DetectionRule detectionRule : detectionRules) {
            // cooldown 안에 같은 지갑·같은 유형 이벤트가 이미 있으면 룰 평가 자체를 건너뛴다.
            // 발화 폭증 방지가 목적이고, 부수적으로 룰의 창 집계 쿼리도 절감된다(catch-up 성능).
            if (isInCooldown(detectionRule, transaction)) {
                continue;
            }
            detectionRule.evaluate(transaction)
                    .filter(command -> !detectionEventRepository.existsByTransactionIdAndEventType(
                            command.transaction().getId(),
                            command.eventType()
                    ))
                    .map(this::toDetectionEvent)
                    .map(detectionEventRepository::save)
                    .ifPresent(event -> {
                        metrics.recordDetectionEvent(event.getEventType().name(), event.getRiskLevel().name());
                        kafkaProducer.publishDetectedEvent(
                                DetectedEventMessage.from(event),
                                event.getTransaction() != null ? event.getTransaction().getTxHash() : String.valueOf(event.getId())
                        );
                    });
        }
        processingTracker.record("detection", System.nanoTime() - startedNanos);
    }

    /**
     * 룰이 cooldown 대상을 선언했고(fromAddress 계약) 창 안에 기존 이벤트가 있으면 true.
     * detectedAt이 벽시계 기준이므로 cutoff도 현재 시각 기준으로 계산한다 — catch-up으로
     * 과거 블록을 처리 중이어도 실시간 30분당 지갑별 1건으로 발화가 억제된다.
     */
    private boolean isInCooldown(DetectionRule detectionRule, Transaction transaction) {
        EventType cooldownType = detectionRule.cooldownEventType();
        if (cooldownType == null || detectionProperties.ruleCooldownMinutes() <= 0) {
            return false;
        }
        Instant cutoff = Instant.now()
                .minus(detectionProperties.ruleCooldownMinutes(), java.time.temporal.ChronoUnit.MINUTES);
        return detectionEventRepository.existsByWalletAddressAndEventTypeAndDetectedAtAfter(
                transaction.getFromAddress(), cooldownType, cutoff);
    }

    private static String shortHash(String txHash) {
        if (txHash == null) {
            return "(hash 없음)";
        }
        return txHash.length() <= 14 ? txHash : txHash.substring(0, 14) + "…";
    }

    private DetectionEvent toDetectionEvent(DetectionCommand command) {
        DetectionEvent event = new DetectionEvent(
                command.eventType(),
                command.riskLevel(),
                command.riskScore(),
                command.summary(),
                command.walletAddress(),
                Instant.now(),
                command.transaction()
        );
        event.attachRuleEvidence(command.ruleVersion(), serializeEvidence(command));
        return event;
    }

    /**
     * evidence를 {"rule": ..., "ruleVersion": ..., ...룰별 필드} 형태의 JSON으로 직렬화한다.
     * 직렬화 실패는 탐지 자체를 막지 않도록 null(evidence 없음)로 강등한다.
     */
    private String serializeEvidence(DetectionCommand command) {
        if (command.ruleName() == null && (command.evidence() == null || command.evidence().isEmpty())) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rule", command.ruleName());
        payload.put("ruleVersion", command.ruleVersion());
        if (command.evidence() != null) {
            payload.putAll(command.evidence());
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            log.warn("Failed to serialize detection evidence for rule {} (eventType={}); storing event without evidence",
                    command.ruleName(), command.eventType(), exception);
            return null;
        }
    }
}
