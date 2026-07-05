package com.chainwatch.backend.collector.client;

import com.chainwatch.backend.collector.dto.BlockDto;
import com.chainwatch.backend.collector.util.RequestThrottle;

/**
 * 공급자 rate limit 보호를 위해 요청 간 최소 간격을 강제하는 데코레이터.
 */
public class ThrottledBlockchainClient implements BlockchainClient {

    private final BlockchainClient delegate;
    private final RequestThrottle throttle;

    public ThrottledBlockchainClient(BlockchainClient delegate, RequestThrottle throttle) {
        this.delegate = delegate;
        this.throttle = throttle;
    }

    @Override
    public long fetchLatestBlockNumber() {
        throttle.acquire();
        return delegate.fetchLatestBlockNumber();
    }

    @Override
    public BlockDto fetchBlock(long blockNumber) {
        throttle.acquire();
        return delegate.fetchBlock(blockNumber);
    }

    @Override
    public String network() {
        return delegate.network();
    }
}
