package com.chainwatch.backend.collector.client;

import com.chainwatch.backend.collector.dto.BlockDto;
import com.chainwatch.backend.collector.metrics.CollectorMetrics;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * RPC 호출 지연 시간을 기록하는 데코레이터.
 */
public class MeteredBlockchainClient implements BlockchainClient {

    private final BlockchainClient delegate;
    private final CollectorMetrics metrics;

    public MeteredBlockchainClient(BlockchainClient delegate, CollectorMetrics metrics) {
        this.delegate = delegate;
        this.metrics = metrics;
    }

    @Override
    public long fetchLatestBlockNumber() {
        return timed(delegate::fetchLatestBlockNumber);
    }

    @Override
    public BlockDto fetchBlock(long blockNumber) {
        return timed(() -> delegate.fetchBlock(blockNumber));
    }

    @Override
    public String network() {
        return delegate.network();
    }

    private <T> T timed(Supplier<T> call) {
        long startedAt = System.nanoTime();
        try {
            return call.get();
        } finally {
            metrics.recordRpcLatency(Duration.ofNanos(System.nanoTime() - startedAt));
        }
    }
}
