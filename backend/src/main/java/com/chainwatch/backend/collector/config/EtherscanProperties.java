package com.chainwatch.backend.collector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainwatch.etherscan")
public record EtherscanProperties(
        String baseUrl,
        String apiKey,
        String chainId
) {
}
