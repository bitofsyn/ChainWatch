package com.chainwatch.backend.messaging.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic collectedTransactionsTopic(KafkaTopicProperties properties) {
        return TopicBuilder.name(properties.collectedTransactions())
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic detectedEventsTopic(KafkaTopicProperties properties) {
        return TopicBuilder.name(properties.detectedEvents())
                .partitions(3)
                .replicas(1)
                .build();
    }
}
