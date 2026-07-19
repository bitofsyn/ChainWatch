package com.chainwatch.backend.detection.rule;

import com.chainwatch.backend.detection.config.DetectionThresholds;
import com.chainwatch.backend.detection.config.DetectionThresholdsProvider;
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

    private final DetectionThresholdsProvider thresholds;
    private final TransactionRepository transactionRepository;

    public RapidTransferDetectionRule(
            DetectionThresholdsProvider thresholds,
            TransactionRepository transactionRepository
    ) {
        this.thresholds = thresholds;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public EventType cooldownEventType() {
        // walletAddress = fromAddress 계약 (DetectionRule.cooldownEventType 문서 참조)
        return EventType.RAPID_TRANSFER;
    }

    @Override
    public Optional<DetectionCommand> evaluate(Transaction transaction) {
        DetectionThresholds current = thresholds.current();
        if (current.rapidTransferThresholdCount() <= 1) {
            return Optional.empty();
        }

        Instant thresholdTime = transaction.getTimestamp()
                .minus(current.rapidTransferWindowMinutes(), ChronoUnit.MINUTES);

        long recentTransferCount = transactionRepository.countRecentTransfersFromAddress(
                transaction.getFromAddress(),
                thresholdTime
        );

        if (recentTransferCount < current.rapidTransferThresholdCount()) {
            return Optional.empty();
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("windowMinutes", current.rapidTransferWindowMinutes());
        evidence.put("thresholdCount", current.rapidTransferThresholdCount());
        evidence.put("observedTransferCount", recentTransferCount);
        evidence.put("windowStart", thresholdTime.toString());
        evidence.put("fromAddress", transaction.getFromAddress());

        return Optional.of(new DetectionCommand(
                EventType.RAPID_TRANSFER,
                RiskLevel.MEDIUM,
                72,
                "Rapid transfer pattern detected: " + recentTransferCount
                        + " transfers from " + transaction.getFromAddress()
                        + " within " + current.rapidTransferWindowMinutes() + " minutes",
                transaction.getFromAddress(),
                transaction,
                RULE_NAME,
                RULE_VERSION,
                evidence
        ));
    }
}
