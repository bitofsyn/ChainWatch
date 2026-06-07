package com.chainwatch.backend.detection.service;

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

@Service
public class DetectionService {

    private final List<DetectionRule> detectionRules;
    private final DetectionEventRepository detectionEventRepository;
    private final ChainWatchKafkaProducer kafkaProducer;

    public DetectionService(
            List<DetectionRule> detectionRules,
            DetectionEventRepository detectionEventRepository,
            ChainWatchKafkaProducer kafkaProducer
    ) {
        this.detectionRules = detectionRules;
        this.detectionEventRepository = detectionEventRepository;
        this.kafkaProducer = kafkaProducer;
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
                    .ifPresent(event -> kafkaProducer.publishDetectedEvent(
                            DetectedEventMessage.from(event),
                            event.getTransaction() != null ? event.getTransaction().getTxHash() : String.valueOf(event.getId())
                    ));
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
