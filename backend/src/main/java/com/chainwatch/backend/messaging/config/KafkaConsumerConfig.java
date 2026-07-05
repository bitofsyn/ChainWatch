package com.chainwatch.backend.messaging.config;

import com.chainwatch.backend.collector.kafka.RawTransactionEvent;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(prefix = "spring.kafka", name = "bootstrap-servers")
public class KafkaConsumerConfig {

    /**
     * 탐지 파이프라인용 raw-transactions consumer.
     * 처리 실패 시 1초 간격 3회 재시도 후 DLT(원본 토픽명 + ".DLT")로 격리해
     * poison message가 파티션을 막지 않도록 한다.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RawTransactionEvent>
    rawTransactionKafkaListenerContainerFactory(
            KafkaProperties kafkaProperties,
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        ConcurrentKafkaListenerContainerFactory<String, RawTransactionEvent> factory = listenerContainerFactory(
                kafkaProperties,
                RawTransactionEvent.class,
                "chainwatch-detection"
        );
        DeadLetterPublishingRecoverer deadLetterRecoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        factory.setCommonErrorHandler(new DefaultErrorHandler(deadLetterRecoverer, new FixedBackOff(1_000L, 3)));
        return factory;
    }

    /** 대시보드 피드 캐시용 raw-transactions consumer (탐지 그룹과 별도 그룹으로 독립 소비). */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RawTransactionEvent>
    rawTransactionFeedKafkaListenerContainerFactory(KafkaProperties kafkaProperties) {
        return listenerContainerFactory(
                kafkaProperties,
                RawTransactionEvent.class,
                "chainwatch-feed-transactions"
        );
    }

    /** raw-transactions DLT 모니터링용 consumer. */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RawTransactionEvent>
    rawTransactionDltKafkaListenerContainerFactory(KafkaProperties kafkaProperties) {
        return listenerContainerFactory(
                kafkaProperties,
                RawTransactionEvent.class,
                "chainwatch-dlt-monitor"
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
                        ? kafkaProperties.getConsumer().getAutoOffsetReset()
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
