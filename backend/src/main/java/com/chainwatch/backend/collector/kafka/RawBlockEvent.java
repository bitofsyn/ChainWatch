package com.chainwatch.backend.collector.kafka;

import com.chainwatch.backend.collector.dto.BlockDto;
import java.math.BigInteger;
import java.time.Instant;

/**
 * raw-blocks 토픽으로 발행되는 블록 이벤트. 불변 객체.
 */
public record RawBlockEvent(
        long blockNumber,
        String blockHash,
        String parentHash,
        Instant timestamp,
        String miner,
        BigInteger gasUsed,
        BigInteger gasLimit,
        int transactionCount,
        String network,
        Instant collectedAt
) {

    public static RawBlockEvent from(BlockDto block, Instant collectedAt) {
        return new RawBlockEvent(
                block.blockNumber(),
                block.blockHash(),
                block.parentHash(),
                block.timestamp(),
                block.miner(),
                block.gasUsed(),
                block.gasLimit(),
                block.transactionCount(),
                block.network(),
                collectedAt
        );
    }
}
