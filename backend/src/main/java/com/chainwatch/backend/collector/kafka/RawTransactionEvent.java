package com.chainwatch.backend.collector.kafka;

import com.chainwatch.backend.collector.dto.TransactionDto;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;

/**
 * raw-transactions 토픽으로 발행되는 트랜잭션 이벤트. Detection Server의 입력이 된다. 불변 객체.
 */
public record RawTransactionEvent(
        String txHash,
        long blockNumber,
        String fromAddress,
        String toAddress,
        BigDecimal valueEth,
        BigInteger gas,
        BigInteger gasPriceWei,
        BigInteger maxFeePerGasWei,
        BigInteger maxPriorityFeePerGasWei,
        BigInteger nonce,
        String inputData,
        String transactionType,
        String contractAddress,
        Instant timestamp,
        String network,
        Instant collectedAt
) {

    public static RawTransactionEvent from(TransactionDto transaction, Instant collectedAt) {
        return new RawTransactionEvent(
                transaction.txHash(),
                transaction.blockNumber(),
                transaction.fromAddress(),
                transaction.toAddress(),
                transaction.valueEth(),
                transaction.gas(),
                transaction.gasPriceWei(),
                transaction.maxFeePerGasWei(),
                transaction.maxPriorityFeePerGasWei(),
                transaction.nonce(),
                transaction.inputData(),
                transaction.transactionType(),
                transaction.contractAddress(),
                transaction.timestamp(),
                transaction.network(),
                collectedAt
        );
    }
}
