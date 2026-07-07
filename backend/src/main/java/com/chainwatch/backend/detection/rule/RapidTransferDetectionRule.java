package com.chainwatch.backend.detection.rule;

import com.chainwatch.backend.detection.config.DetectionProperties;
import com.chainwatch.backend.detection.domain.DetectionCommand;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import com.chainwatch.backend.transaction.domain.Transaction;
import com.chainwatch.backend.transaction.repository.TransactionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RapidTransferDetectionRule implements DetectionRule {

    static final String RULE_NAME = "rapid-transfer";
    static final String RULE_VERSION = "1.0";

    private final DetectionProperties detectionProperties;
    private final TransactionRepository transactionRepository;

    public RapidTransferDetectionRule(
            DetectionProperties detectionProperties,
            TransactionRepository transactionRepository
    ) {
        this.detectionProperties = detectionProperties;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public Optional<DetectionCommand> evaluate(Transaction transaction) {
        if (detectionProperties.rapidTransferThresholdCount() <= 1) {
            return Optional.empty();
        }

        Instant thresholdTime = transaction.getTimestamp()
                .minus(detectionProperties.rapidTransferWindowMinutes(), ChronoUnit.MINUTES);

        long recentTransferCount = transactionRepository.countRecentTransfersFromAddress(
                transaction.getFromAddress(),
                thresholdTime
        );

        if (recentTransferCount < detectionProperties.rapidTransferThresholdCount()) {
            return Optional.empty();
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("windowMinutes", detectionProperties.rapidTransferWindowMinutes());
        evidence.put("thresholdCount", detectionProperties.rapidTransferThresholdCount());
        evidence.put("observedTransferCount", recentTransferCount);
        evidence.put("windowStart", thresholdTime.toString());
        evidence.put("fromAddress", transaction.getFromAddress());

        return Optional.of(new DetectionCommand(
                EventType.RAPID_TRANSFER,
                RiskLevel.MEDIUM,
                72,
                "Rapid transfer pattern detected: " + recentTransferCount
                        + " transfers from " + transaction.getFromAddress()
                        + " within " + detectionProperties.rapidTransferWindowMinutes() + " minutes",
                transaction.getFromAddress(),
                transaction,
                RULE_NAME,
                RULE_VERSION,
                evidence
        ));
    }
}
