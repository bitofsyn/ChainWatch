package com.chainwatch.backend.transaction.api;

import com.chainwatch.backend.transaction.domain.Transaction;
import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        Long id,
        String txHash,
        String fromAddress,
        String toAddress,
        BigDecimal amount,
        BigDecimal gasFee,
        Long blockNumber,
        Instant timestamp,
        String contractAddress
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getTxHash(),
                transaction.getFromAddress(),
                transaction.getToAddress(),
                transaction.getAmount(),
                transaction.getGasFee(),
                transaction.getBlockNumber(),
                transaction.getTimestamp(),
                transaction.getContractAddress()
        );
    }
}
