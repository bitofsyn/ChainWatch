package com.chainwatch.backend.collector.client;

import com.chainwatch.backend.collector.dto.BlockDto;
import com.chainwatch.backend.collector.exception.CollectorConfigurationException;

/**
 * 수집 소스가 설정되지 않았을 때의 폴백. 호출 시 설정 안내와 함께 실패한다.
 */
public class NoOpBlockchainClient implements BlockchainClient {

    private final String reason;
    private final String network;

    public NoOpBlockchainClient(String reason, String network) {
        this.reason = reason;
        this.network = network;
    }

    @Override
    public long fetchLatestBlockNumber() {
        throw configurationError();
    }

    @Override
    public BlockDto fetchBlock(long blockNumber) {
        throw configurationError();
    }

    @Override
    public String network() {
        return network;
    }

    private CollectorConfigurationException configurationError() {
        return new CollectorConfigurationException(
                "No blockchain source is configured: " + reason
                        + " (set chainwatch.collector.provider and the matching connection settings)");
    }
}
