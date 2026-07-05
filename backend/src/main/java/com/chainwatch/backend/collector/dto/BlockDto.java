package com.chainwatch.backend.collector.dto;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

/**
 * 체인에서 수집한 블록의 정규화 표현. 불변 객체.
 */
public record BlockDto(
        long blockNumber,
        String blockHash,
        String parentHash,
        Instant timestamp,
        String miner,
        BigInteger gasUsed,
        BigInteger gasLimit,
        String network,
        List<TransactionDto> transactions
) {

    public BlockDto {
        transactions = transactions == null ? List.of() : List.copyOf(transactions);
    }

    public int transactionCount() {
        return transactions.size();
    }
}
