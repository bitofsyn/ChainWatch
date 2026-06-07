package com.chainwatch.backend.collector.client;

import java.math.BigDecimal;

public record CollectedTransaction(
        String txHash,
        String fromAddress,
        String toAddress,
        BigDecimal amount,
        BigDecimal gasFee,
        String contractAddress
) {
}
