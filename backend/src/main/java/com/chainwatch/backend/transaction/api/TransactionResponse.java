package com.chainwatch.backend.transaction.api;

import com.chainwatch.backend.collector.service.ChainFinalityService.Confirmation;
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
        String contractAddress,
        Long confirmations,
        Boolean confirmed
) {
    public static TransactionResponse from(Transaction transaction) {
        return from(transaction, Confirmation.UNKNOWN);
    }

    /** confirmations/confirmed는 체인 head를 아직 관측하지 못한 경우 null (additive nullable 필드). */
    public static TransactionResponse from(Transaction transaction, Confirmation confirmation) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getTxHash(),
                transaction.getFromAddress(),
                transaction.getToAddress(),
                transaction.getAmount(),
                transaction.getGasFee(),
                transaction.getBlockNumber(),
                transaction.getTimestamp(),
                transaction.getContractAddress(),
                confirmation.confirmations(),
                confirmation.confirmed()
        );
    }
}
