package com.chainwatch.backend.wallet.api;

import java.time.Instant;
import java.util.List;

public record WalletSummaryResponse(
        String walletAddress,
        long eventCount,
        int maxRiskScore,
        Instant firstDetectedAt,
        Instant lastDetectedAt,
        List<KeyCount> eventTypeCounts,
        List<KeyCount> riskLevelCounts
) {
    public record KeyCount(String key, long count) {
    }
}
