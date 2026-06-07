package com.chainwatch.backend.analysis.client;

public record AiAnalysisRequest(
        Long detectionEventId,
        String provider,
        String model,
        String eventType,
        String riskLevel,
        Integer riskScore,
        String walletAddress,
        String txHash,
        String summary
) {
}
