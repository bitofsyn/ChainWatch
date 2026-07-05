package com.chainwatch.backend.collector.dto;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;

/**
 * 체인에서 수집한 트랜잭션의 정규화 표현. 불변 객체이며 Web3j 등 클라이언트 구현체 타입에 의존하지 않는다.
 *
 * @param valueEth              전송 금액(ETH 단위)
 * @param gasPriceWei           legacy gas price 또는 노드가 계산한 effective gas price (없으면 null)
 * @param maxFeePerGasWei       EIP-1559 max fee (legacy 트랜잭션이면 null)
 * @param maxPriorityFeePerGasWei EIP-1559 priority fee (legacy 트랜잭션이면 null)
 * @param toAddress             수신 주소 (컨트랙트 생성 트랜잭션이면 null)
 * @param contractAddress       생성되었거나 호출된 컨트랙트 주소 (해당 없으면 null)
 */
public record TransactionDto(
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
        String network
) {
}
