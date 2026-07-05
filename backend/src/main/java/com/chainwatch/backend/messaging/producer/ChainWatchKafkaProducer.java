package com.chainwatch.backend.messaging.producer;

import com.chainwatch.backend.messaging.config.KafkaTopicProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ChainWatchKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties kafkaTopicProperties;

    public ChainWatchKafkaProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaTopicProperties kafkaTopicProperties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaTopicProperties = kafkaTopicProperties;
    }

    public void publishDetectedEvent(DetectedEventMessage eventMessage, String key) {
        kafkaTemplate.send(kafkaTopicProperties.detectedEvents(), key, eventMessage);
    }
}
