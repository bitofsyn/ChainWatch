package com.chainwatch.backend.collector.api;

public record CollectorResponse(
        long blockNumber,
        int savedTransactionCount,
        String network
) {
}
