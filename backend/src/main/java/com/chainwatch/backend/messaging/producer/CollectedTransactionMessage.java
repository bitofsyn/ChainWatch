package com.chainwatch.backend.messaging.producer;

import com.chainwatch.backend.transaction.domain.Transaction;
import java.math.BigDecimal;
import java.time.Instant;

public record CollectedTransactionMessage(
        Long transactionId,
        String txHash,
        String fromAddress,
        String toAddress,
        BigDecimal amount,
        BigDecimal gasFee,
        Long blockNumber,
        Instant timestamp,
        String contractAddress
) {
    public static CollectedTransactionMessage from(Transaction transaction) {
        return new CollectedTransactionMessage(
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
