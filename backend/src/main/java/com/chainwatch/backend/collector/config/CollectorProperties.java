package com.chainwatch.backend.collector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainwatch.collector")
public record CollectorProperties(
        String provider,
        boolean enabled,
        long fixedDelayMs,
        long startBlock
) {
}
