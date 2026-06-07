package com.chainwatch.backend.detection.domain;

import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import com.chainwatch.backend.transaction.domain.Transaction;

public record DetectionCommand(
        EventType eventType,
        RiskLevel riskLevel,
        int riskScore,
        String summary,
        String walletAddress,
        Transaction transaction
) {
}
