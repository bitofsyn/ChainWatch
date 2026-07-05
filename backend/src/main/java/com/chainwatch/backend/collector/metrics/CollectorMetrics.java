package com.chainwatch.backend.collector.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * 수집기 운영 메트릭. Prometheus 연동 시 /actuator/prometheus로 노출된다.
 */
@Component
public class CollectorMetrics {

    private final Counter blocksCollected;
    private final Counter transactionsCollected;
    private final Counter rawBlocksPublished;
    private final Counter rawTransactionsPublished;
    private final Counter retries;
    private final Counter errors;
    private final Counter websocketReconnects;
    private final Counter reorgs;
    private final Timer rpcLatency;

    public CollectorMetrics(MeterRegistry registry) {
        this.blocksCollected = Counter.builder("chainwatch.collector.blocks.collected")
                .description("Number of blocks collected")
                .register(registry);
        this.transactionsCollected = Counter.builder("chainwatch.collector.transactions.collected")
                .description("Number of transactions collected")
                .register(registry);
        this.rawBlocksPublished = Counter.builder("chainwatch.collector.kafka.raw.blocks.published")
                .description("Raw block events published to Kafka")
                .register(registry);
        this.rawTransactionsPublished = Counter.builder("chainwatch.collector.kafka.raw.transactions.published")
                .description("Raw transaction events published to Kafka")
                .register(registry);
        this.retries = Counter.builder("chainwatch.collector.retries")
                .description("RPC retry attempts")
                .register(registry);
        this.errors = Counter.builder("chainwatch.collector.errors")
                .description("Collector errors")
                .register(registry);
        this.websocketReconnects = Counter.builder("chainwatch.collector.websocket.reconnects")
                .description("WebSocket reconnect attempts")
                .register(registry);
        this.reorgs = Counter.builder("chainwatch.collector.reorgs")
                .description("Chain reorganizations detected")
                .register(registry);
        this.rpcLatency = Timer.builder("chainwatch.collector.rpc.latency")
                .description("Latency of blockchain RPC calls")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void recordBlockCollected(int transactionCount) {
        blocksCollected.increment();
        transactionsCollected.increment(transactionCount);
    }

    public void recordRawBlockPublished() {
        rawBlocksPublished.increment();
    }

    public void recordRawTransactionsPublished(int count) {
        rawTransactionsPublished.increment(count);
    }

    public void incrementRetry(String operationName) {
        retries.increment();
    }

    public void incrementError() {
        errors.increment();
    }

    public void incrementWebsocketReconnect() {
        websocketReconnects.increment();
    }

    public void incrementReorg() {
        reorgs.increment();
    }

    public void recordRpcLatency(Duration duration) {
        rpcLatency.record(duration);
    }
}
