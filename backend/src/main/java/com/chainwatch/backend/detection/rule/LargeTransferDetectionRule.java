package com.chainwatch.backend.detection.rule;

import com.chainwatch.backend.detection.config.DetectionThresholdsProvider;
import com.chainwatch.backend.detection.domain.DetectionCommand;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import com.chainwatch.backend.transaction.domain.Transaction;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class LargeTransferDetectionRule implements DetectionRule {

    static final String RULE_NAME = "large-transfer";
    static final String RULE_VERSION = "1.0";

    private final DetectionThresholdsProvider thresholds;

    public LargeTransferDetectionRule(DetectionThresholdsProvider thresholds) {
        this.thresholds = thresholds;
    }

    @Override
    public Optional<DetectionCommand> evaluate(Transaction transaction) {
        BigDecimal threshold = thresholds.current().largeTransferThresholdEth();
        if (threshold == null || transaction.getAmount().compareTo(threshold) < 0) {
            return Optional.empty();
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("thresholdEth", threshold);
        evidence.put("observedAmountEth", transaction.getAmount());
        evidence.put("fromAddress", transaction.getFromAddress());
        evidence.put("toAddress", transaction.getToAddress());

        return Optional.of(new DetectionCommand(
                EventType.LARGE_TRANSFER,
                RiskLevel.HIGH,
                85,
                "Large transfer detected: " + transaction.getAmount() + " ETH moved to " + transaction.getToAddress(),
                transaction.getToAddress(),
                transaction,
                RULE_NAME,
                RULE_VERSION,
                evidence
        ));
    }
}
