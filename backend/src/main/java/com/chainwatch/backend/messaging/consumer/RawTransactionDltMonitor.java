package com.chainwatch.backend.messaging.consumer;

import com.chainwatch.backend.agentops.service.AgentFailureRecorder;
import com.chainwatch.backend.collector.kafka.RawTransactionEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * raw-transactions DLT 모니터. 재시도가 소진되어 격리된 메시지를 로그와 메트릭으로 노출해
 * 운영자가 poison message를 인지하고 수동 재처리할 수 있게 한다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.kafka", name = "bootstrap-servers")
public class RawTransactionDltMonitor {

    private static final Logger log = LoggerFactory.getLogger(RawTransactionDltMonitor.class);

    private final Counter dltMessages;
    private final AgentFailureRecorder failureRecorder;

    public RawTransactionDltMonitor(MeterRegistry meterRegistry, AgentFailureRecorder failureRecorder) {
        this.dltMessages = Counter.builder("chainwatch.detection.dlt.messages")
                .description("Raw transaction messages routed to the dead letter topic")
                .register(meterRegistry);
        this.failureRecorder = failureRecorder;
    }

    @KafkaListener(
            topics = "${chainwatch.kafka.topics.raw-transactions}.DLT",
            containerFactory = "rawTransactionDltKafkaListenerContainerFactory"
    )
    public void monitor(
            RawTransactionEvent event,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String exceptionMessage
    ) {
        dltMessages.increment();
        failureRecorder.record("detection",
                "트랜잭션 " + event.txHash() + " DLT 격리",
                "재시도 소진으로 dead letter topic 이동: " + exceptionMessage, false);
        log.error("[ERROR] Raw transaction {} (block {}) moved to DLT: {}",
                event.txHash(), event.blockNumber(), exceptionMessage);
    }
}
