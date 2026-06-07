package com.chainwatch.backend.feed.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainwatch.feed")
public record FeedCacheProperties(
        String recentTransactionsKey,
        String recentEventsKey,
        int maxSize
) {
}
