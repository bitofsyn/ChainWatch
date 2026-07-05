package com.chainwatch.backend.event.api;

import java.time.Instant;
import java.util.List;

public record EventStatsResponse(
        long totalEvents,
        long last24hEvents,
        List<KeyCount> riskLevelCounts,
        List<KeyCount> eventTypeCounts,
        List<KeyCount> statusCounts,
        List<WalletCount> topWallets
) {
    public record KeyCount(String key, long count) {
    }

    public record WalletCount(
            String walletAddress,
            long eventCount,
            int maxRiskScore,
            Instant lastDetectedAt
    ) {
    }
}
