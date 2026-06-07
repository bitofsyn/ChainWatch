package com.chainwatch.backend.collector.client;

import java.time.Instant;
import java.util.List;

public record CollectedBlock(
        long blockNumber,
        Instant timestamp,
        List<CollectedTransaction> transactions
) {
}
