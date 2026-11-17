package com.chainwatch.backend.messaging.producer;

import com.chainwatch.backend.messaging.config.KafkaTopicProperties;
import com.chainwatch.backend.ops.config.InfraProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ChainWatchKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties kafkaTopicProperties;
    /** false면 Kafka 미사용 구성: 발행을 건너뛴다. */
    private final boolean kafkaEnabled;

    @Autowired
    public ChainWatchKafkaProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaTopicProperties kafkaTopicProperties,
            InfraProperties infraProperties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaTopicProperties = kafkaTopicProperties;
        this.kafkaEnabled = infraProperties.kafkaEnabled();
    }

    public void publishDetectedEvent(DetectedEventMessage eventMessage, String key) {
        if (!kafkaEnabled) {
            return;
        }
        kafkaTemplate.send(kafkaTopicProperties.detectedEvents(), key, eventMessage);
    }
}
