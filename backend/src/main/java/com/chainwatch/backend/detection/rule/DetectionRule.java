package com.chainwatch.backend.detection.rule;

import com.chainwatch.backend.detection.domain.DetectionCommand;
import com.chainwatch.backend.transaction.domain.Transaction;
import java.util.Optional;

public interface DetectionRule {
    Optional<DetectionCommand> evaluate(Transaction transaction);
}
