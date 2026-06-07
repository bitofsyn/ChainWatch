package com.chainwatch.backend.collector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainwatch.ethereum")
public record EthereumProperties(
        String rpcUrl,
        String network
) {
}
