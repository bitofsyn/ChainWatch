package com.chainwatch.backend.collector.api;

public record CollectorResponse(
        long blockNumber,
        int transactionCount,
        int savedTransactionCount,
        String network,
        String provider
) {
}
