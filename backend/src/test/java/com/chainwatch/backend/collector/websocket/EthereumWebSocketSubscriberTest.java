package com.chainwatch.backend.collector.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainwatch.backend.collector.config.CollectionMode;
import com.chainwatch.backend.collector.config.CollectorProperties;
import com.chainwatch.backend.collector.config.EthereumProperties;
import com.chainwatch.backend.collector.config.ProviderType;
import com.chainwatch.backend.collector.metrics.CollectorMetrics;
import com.chainwatch.backend.collector.service.BlockCollectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.reactivex.processors.PublishProcessor;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.websocket.events.NewHeadsNotification;

class EthereumWebSocketSubscriberTest {

    private final BlockCollectionService blockCollectionService = mock(BlockCollectionService.class);
    private final CollectorMetrics metrics = new CollectorMetrics(new SimpleMeterRegistry());

    private EthereumWebSocketSubscriber subscriber;

    @AfterEach
    void tearDown() {
        if (subscriber != null) {
            subscriber.stop();
        }
    }

    @Test
    void newHeadTriggersCatchUpCollection() throws Exception {
        PublishProcessor<NewHeadsNotification> heads = PublishProcessor.create();
        Web3j web3j = mock(Web3j.class);
        when(web3j.newHeadsNotifications()).thenReturn(heads);
        AtomicInteger connects = new AtomicInteger();
        Web3jWebSocketConnector connector = () -> {
            connects.incrementAndGet();
            return new Web3jWebSocketConnector.WebSocketConnection(web3j, () -> {
            });
        };

        subscriber = subscriber(connector, 3);
        subscriber.start();
        waitUntil(() -> connects.get() == 1);

        heads.onNext(newHead("0x2a"));

        verify(blockCollectionService, timeout(2_000)).collectUpTo(42L);
    }

    @Test
    void reconnectsWithBackoffAfterConnectFailure() throws Exception {
        PublishProcessor<NewHeadsNotification> heads = PublishProcessor.create();
        Web3j web3j = mock(Web3j.class);
        when(web3j.newHeadsNotifications()).thenReturn(heads);
        AtomicInteger connects = new AtomicInteger();
        Web3jWebSocketConnector connector = () -> {
            if (connects.incrementAndGet() == 1) {
                throw new IOException("connection refused");
            }
            return new Web3jWebSocketConnector.WebSocketConnection(web3j, () -> {
            });
        };

        subscriber = subscriber(connector, 3);
        subscriber.start();

        waitUntil(() -> connects.get() >= 2);
        assertThat(subscriber.isRunning()).isTrue();
    }

    @Test
    void reconnectsAfterStreamErrorAndResetsAttemptCounter() throws Exception {
        PublishProcessor<NewHeadsNotification> firstStream = PublishProcessor.create();
        PublishProcessor<NewHeadsNotification> secondStream = PublishProcessor.create();
        Web3j web3j = mock(Web3j.class);
        when(web3j.newHeadsNotifications()).thenReturn(firstStream, secondStream);
        AtomicInteger connects = new AtomicInteger();
        Web3jWebSocketConnector connector = () -> {
            connects.incrementAndGet();
            return new Web3jWebSocketConnector.WebSocketConnection(web3j, () -> {
            });
        };

        subscriber = subscriber(connector, 3);
        subscriber.start();
        waitUntil(() -> connects.get() == 1);

        firstStream.onError(new IOException("socket closed"));

        waitUntil(() -> connects.get() == 2);
        waitUntil(() -> subscriber.currentReconnectAttempts() == 0);
        assertThat(subscriber.isRunning()).isTrue();
    }

    @Test
    void stopsAfterReconnectAttemptsAreExhausted() throws Exception {
        AtomicInteger connects = new AtomicInteger();
        Web3jWebSocketConnector alwaysFailing = () -> {
            connects.incrementAndGet();
            throw new IOException("connection refused");
        };

        subscriber = subscriber(alwaysFailing, 3);
        subscriber.start();

        waitUntil(() -> !subscriber.isRunning());
        assertThat(connects.get()).isEqualTo(4);
    }

    private EthereumWebSocketSubscriber subscriber(Web3jWebSocketConnector connector, int maxReconnectAttempts) {
        CollectorProperties properties = new CollectorProperties(
                ProviderType.RPC, CollectionMode.WEBSOCKET, true, 15_000, 0, 5, 6, 12,
                null,
                new CollectorProperties.Backoff(maxReconnectAttempts, 1, 1.0, 5),
                null);
        EthereumProperties ethereumProperties = new EthereumProperties(
                "ethereum-mainnet", "1", null, "wss://example.invalid/ws", 30);
        return new EthereumWebSocketSubscriber(
                properties, ethereumProperties, connector, blockCollectionService, metrics);
    }

    private NewHeadsNotification newHead(String numberHex) throws Exception {
        String json = """
                {"jsonrpc":"2.0","method":"eth_subscription",
                 "params":{"subscription":"0xcd0c3e8af590364c09d0fa6a1210faf5",
                           "result":{"number":"%s"}}}
                """.formatted(numberHex);
        return new ObjectMapper().readValue(json, NewHeadsNotification.class);
    }

    private void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Condition not met within timeout");
            }
            Thread.sleep(10);
        }
    }
}
