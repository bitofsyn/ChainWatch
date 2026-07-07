package com.chainwatch.backend.collector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainwatch.collector")
public record CollectorProperties(
        ProviderType provider,
        CollectionMode mode,
        boolean enabled,
        long pollIntervalMs,
        long startBlock,
        int maxBlocksPerPoll,
        int reorgRewindDepth,
        int confirmationDepth,
        Backoff retry,
        Backoff websocketReconnect,
        Throttle throttle
) {

    public CollectorProperties {
        if (provider == null) {
            provider = ProviderType.RPC;
        }
        if (mode == null) {
            mode = CollectionMode.POLLING;
        }
        if (pollIntervalMs <= 0) {
            pollIntervalMs = 15_000L;
        }
        if (maxBlocksPerPoll <= 0) {
            maxBlocksPerPoll = 5;
        }
        if (reorgRewindDepth <= 0) {
            reorgRewindDepth = 6;
        }
        if (confirmationDepth <= 0) {
            // reorgRewindDepth(6)보다 깊게 잡아 "확정" 데이터는 rewind 범위 밖에 있도록 한다.
            confirmationDepth = 12;
        }
        if (retry == null) {
            retry = new Backoff(0, 0, 0, 0);
        }
        if (websocketReconnect == null) {
            websocketReconnect = new Backoff(10, 1_000L, 2.0, 60_000L);
        }
        if (throttle == null) {
            throttle = new Throttle(0);
        }
    }

    public record Backoff(
            int maxAttempts,
            long initialDelayMs,
            double multiplier,
            long maxDelayMs
    ) {
        public Backoff {
            if (maxAttempts <= 0) {
                maxAttempts = 4;
            }
            if (initialDelayMs <= 0) {
                initialDelayMs = 500L;
            }
            if (multiplier < 1.0) {
                multiplier = 2.0;
            }
            if (maxDelayMs <= 0) {
                maxDelayMs = 15_000L;
            }
        }
    }

    public record Throttle(long minIntervalMs) {
        public Throttle {
            if (minIntervalMs < 0) {
                minIntervalMs = 0;
            }
        }
    }
}
