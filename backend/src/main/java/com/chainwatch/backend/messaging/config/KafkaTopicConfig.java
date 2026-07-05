package com.chainwatch.backend.messaging.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic rawBlocksTopic(KafkaTopicProperties properties) {
        return TopicBuilder.name(properties.rawBlocks())
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic rawTransactionsTopic(KafkaTopicProperties properties) {
        return TopicBuilder.name(properties.rawTransactions())
                .partitions(3)
                .replicas(1)
                .build();
    }

    /** raw-transactions 소비 실패 메시지 격리용 DLT. 파티션 수는 원본 토픽과 일치해야 한다. */
    @Bean
    public NewTopic rawTransactionsDltTopic(KafkaTopicProperties properties) {
        return TopicBuilder.name(properties.rawTransactions() + ".DLT")
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
