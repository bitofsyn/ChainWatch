package com.chainwatch.backend.collector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainwatch.ethereum")
public record EthereumProperties(
        String network,
        String chainId,
        String httpUrl,
        String wsUrl,
        long requestTimeoutSeconds
) {

    public EthereumProperties {
        if (network == null || network.isBlank()) {
            network = "ethereum-mainnet";
        }
        if (requestTimeoutSeconds <= 0) {
            requestTimeoutSeconds = 30;
        }
    }

    public boolean hasHttpUrl() {
        return httpUrl != null && !httpUrl.isBlank();
    }

    public boolean hasWsUrl() {
        return wsUrl != null && !wsUrl.isBlank();
    }
}
