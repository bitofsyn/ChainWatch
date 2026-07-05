package com.chainwatch.backend.detection.api;

import java.util.List;

public record DetectionRulesResponse(
        String mode,
        List<Rule> rules
) {
    public record Rule(
            String eventType,
            String name,
            String description,
            String threshold,
            String baseRiskLevel,
            int baseRiskScore,
            boolean active
    ) {
    }
}
