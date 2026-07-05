package com.chainwatch.backend.messaging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainwatch.kafka.topics")
public record KafkaTopicProperties(
        String rawBlocks,
        String rawTransactions,
        String detectedEvents
) {

    public KafkaTopicProperties {
        if (rawBlocks == null || rawBlocks.isBlank()) {
            rawBlocks = "chainwatch.raw-blocks";
        }
        if (rawTransactions == null || rawTransactions.isBlank()) {
            rawTransactions = "chainwatch.raw-transactions";
        }
    }
}
