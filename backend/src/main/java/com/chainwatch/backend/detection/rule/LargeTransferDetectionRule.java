package com.chainwatch.backend.detection.rule;

import com.chainwatch.backend.detection.config.DetectionProperties;
import com.chainwatch.backend.detection.domain.DetectionCommand;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import com.chainwatch.backend.transaction.domain.Transaction;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class LargeTransferDetectionRule implements DetectionRule {

    private final DetectionProperties detectionProperties;

    public LargeTransferDetectionRule(DetectionProperties detectionProperties) {
        this.detectionProperties = detectionProperties;
    }

    @Override
    public Optional<DetectionCommand> evaluate(Transaction transaction) {
        BigDecimal threshold = detectionProperties.largeTransferThresholdEth();
        if (threshold == null || transaction.getAmount().compareTo(threshold) < 0) {
            return Optional.empty();
        }

        return Optional.of(new DetectionCommand(
                EventType.LARGE_TRANSFER,
                RiskLevel.HIGH,
                85,
                "Large transfer detected: " + transaction.getAmount() + " ETH moved to " + transaction.getToAddress(),
                transaction.getToAddress(),
                transaction
        ));
    }
}
