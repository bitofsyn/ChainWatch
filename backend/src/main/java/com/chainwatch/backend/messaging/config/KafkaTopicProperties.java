package com.chainwatch.backend.messaging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainwatch.kafka.topics")
public record KafkaTopicProperties(
        String collectedTransactions,
        String detectedEvents
) {
}
