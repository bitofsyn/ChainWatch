package com.chainwatch.backend.collector.client;

import com.chainwatch.backend.collector.dto.BlockDto;
import com.chainwatch.backend.collector.util.BackoffRetryExecutor;

/**
 * 일시적 RPC 실패를 지수 백오프로 재시도하는 데코레이터.
 */
public class RetryingBlockchainClient implements BlockchainClient {

    private final BlockchainClient delegate;
    private final BackoffRetryExecutor retryExecutor;

    public RetryingBlockchainClient(BlockchainClient delegate, BackoffRetryExecutor retryExecutor) {
        this.delegate = delegate;
        this.retryExecutor = retryExecutor;
    }

    @Override
    public long fetchLatestBlockNumber() {
        return retryExecutor.execute("fetchLatestBlockNumber", delegate::fetchLatestBlockNumber);
    }

    @Override
    public BlockDto fetchBlock(long blockNumber) {
        return retryExecutor.execute("fetchBlock(" + blockNumber + ")", () -> delegate.fetchBlock(blockNumber));
    }

    @Override
    public String network() {
        return delegate.network();
    }
}
