package com.chainwatch.backend.detection.service;

import com.chainwatch.backend.common.metrics.ChainWatchMetrics;
import com.chainwatch.backend.detection.domain.DetectionCommand;
import com.chainwatch.backend.detection.rule.DetectionRule;
import com.chainwatch.backend.event.domain.DetectionEvent;
import com.chainwatch.backend.event.repository.DetectionEventRepository;
import com.chainwatch.backend.messaging.producer.ChainWatchKafkaProducer;
import com.chainwatch.backend.messaging.producer.DetectedEventMessage;
import com.chainwatch.backend.transaction.domain.Transaction;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 중복 저장 방지는 2중으로 동작한다.
 * 1) 애플리케이션 레벨 exists 체크: 일반 경로에서 중복 저장을 걸러낸다.
 * 2) detection_events (transaction_id, event_type) DB 유니크 제약: 동시 처리/멀티 인스턴스/
 *    Kafka 재소비 경합에서 exists 체크를 동시에 통과해도 한쪽 커밋만 성공한다.
 *    실패한 쪽은 트랜잭션 롤백 후 재처리 시 exists 체크로 수렴하므로 중복 이벤트가 남지 않는다.
 */
@Service
public class DetectionService {

    private final List<DetectionRule> detectionRules;
    private final DetectionEventRepository detectionEventRepository;
    private final ChainWatchKafkaProducer kafkaProducer;
    private final ChainWatchMetrics metrics;

    public DetectionService(
            List<DetectionRule> detectionRules,
            DetectionEventRepository detectionEventRepository,
            ChainWatchKafkaProducer kafkaProducer,
            ChainWatchMetrics metrics
    ) {
        this.detectionRules = detectionRules;
        this.detectionEventRepository = detectionEventRepository;
        this.kafkaProducer = kafkaProducer;
        this.metrics = metrics;
    }

    @Transactional
    public void analyzeTransactions(List<Transaction> transactions) {
        for (Transaction transaction : transactions) {
            analyzeTransaction(transaction);
        }
    }

    @Transactional
    public void analyzeTransaction(Transaction transaction) {
        for (DetectionRule detectionRule : detectionRules) {
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
    }

    private DetectionEvent toDetectionEvent(DetectionCommand command) {
        return new DetectionEvent(
                command.eventType(),
                command.riskLevel(),
                command.riskScore(),
                command.summary(),
                command.walletAddress(),
                Instant.now(),
                command.transaction()
        );
    }
}
