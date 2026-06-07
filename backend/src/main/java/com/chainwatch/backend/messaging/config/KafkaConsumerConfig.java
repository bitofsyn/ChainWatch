package com.chainwatch.backend.messaging.config;

import com.chainwatch.backend.messaging.producer.CollectedTransactionMessage;
import com.chainwatch.backend.messaging.producer.DetectedEventMessage;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

@Configuration
@ConditionalOnProperty(prefix = "spring.kafka", name = "bootstrap-servers")
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CollectedTransactionMessage>
    collectedTransactionKafkaListenerContainerFactory(KafkaProperties kafkaProperties) {
        return listenerContainerFactory(
                kafkaProperties,
                CollectedTransactionMessage.class,
                "chainwatch-feed-transactions"
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DetectedEventMessage>
    detectedEventKafkaListenerContainerFactory(KafkaProperties kafkaProperties) {
        return listenerContainerFactory(
                kafkaProperties,
                DetectedEventMessage.class,
                "chainwatch-feed-events"
        );
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> listenerContainerFactory(
            KafkaProperties kafkaProperties,
            Class<T> targetType,
            String groupId
    ) {
        ConcurrentKafkaListenerContainerFactory<String, T> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory(kafkaProperties, targetType, groupId));
        return factory;
    }

    private <T> ConsumerFactory<String, T> consumerFactory(
            KafkaProperties kafkaProperties,
            Class<T> targetType,
            String groupId
    ) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", kafkaProperties.getBootstrapServers()));
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                kafkaProperties.getConsumer().getAutoOffsetReset() != null
                        ? kafkaProperties.getConsumer().getAutoOffsetReset().name().toLowerCase()
                        : "earliest"
        );

        JsonDeserializer<T> valueDeserializer = new JsonDeserializer<>(targetType, false);
        valueDeserializer.addTrustedPackages("*");

        return new DefaultKafkaConsumerFactory<>(
                properties,
                new StringDeserializer(),
                valueDeserializer
        );
    }
}
