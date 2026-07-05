package com.chainwatch.backend.collector.websocket;

import com.chainwatch.backend.collector.config.CollectionMode;
import com.chainwatch.backend.collector.config.CollectorProperties;
import com.chainwatch.backend.collector.config.EthereumProperties;
import com.chainwatch.backend.collector.exception.CollectorException;
import com.chainwatch.backend.collector.metrics.CollectorMetrics;
import com.chainwatch.backend.collector.service.BlockCollectionService;
import com.chainwatch.backend.collector.util.BackoffRetryExecutor;
import com.chainwatch.backend.collector.websocket.Web3jWebSocketConnector.WebSocketConnection;
import io.reactivex.disposables.Disposable;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.web3j.protocol.websocket.events.NewHeadsNotification;
import org.web3j.utils.Numeric;

/**
 * WebSocket 모드 수집기. newHeads 이벤트를 구독해 새 블록 생성 즉시 수집을 트리거한다.
 * 연결이 끊기면 지수 백오프로 재연결하며(상한 있음), 재연결 중 놓친 블록은
 * {@link BlockCollectionService#collectUpTo(long)}의 catch-up 로직이 복구한다.
 */
@Component
public class EthereumWebSocketSubscriber implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(EthereumWebSocketSubscriber.class);

    private final CollectorProperties collectorProperties;
    private final EthereumProperties ethereumProperties;
    private final Web3jWebSocketConnector connector;
    private final BlockCollectionService blockCollectionService;
    private final CollectorMetrics metrics;

    private final ScheduledExecutorService reconnectScheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> daemonThread(runnable, "collector-ws-reconnect"));
    private final ExecutorService collectExecutor =
            Executors.newSingleThreadExecutor(runnable -> daemonThread(runnable, "collector-ws-worker"));
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    private volatile boolean running;
    private volatile WebSocketConnection connection;
    private volatile Disposable subscription;

    public EthereumWebSocketSubscriber(
            CollectorProperties collectorProperties,
            EthereumProperties ethereumProperties,
            Web3jWebSocketConnector connector,
            BlockCollectionService blockCollectionService,
            CollectorMetrics metrics
    ) {
        this.collectorProperties = collectorProperties;
        this.ethereumProperties = ethereumProperties;
        this.connector = connector;
        this.blockCollectionService = blockCollectionService;
        this.metrics = metrics;
    }

    @Override
    public boolean isAutoStartup() {
        return collectorProperties.enabled()
                && collectorProperties.mode() == CollectionMode.WEBSOCKET
                && ethereumProperties.hasWsUrl();
    }

    @Override
    public void start() {
        running = true;
        log.info("[COLLECTOR_START] mode=websocket network={}", ethereumProperties.network());
        reconnectScheduler.execute(this::connect);
    }

    @Override
    public void stop() {
        running = false;
        closeConnection();
        reconnectScheduler.shutdownNow();
        collectExecutor.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    void connect() {
        if (!running) {
            return;
        }
        try {
            WebSocketConnection newConnection = connector.connect();
            connection = newConnection;
            subscription = newConnection.web3j()
                    .newHeadsNotifications()
                    .subscribe(this::onNewHead, this::onStreamError, this::onStreamCompleted);
            reconnectAttempts.set(0);
            log.info("[WEBSOCKET_CONNECTED] endpoint={} network={}",
                    sanitizeEndpoint(ethereumProperties.wsUrl()), ethereumProperties.network());
        } catch (Exception exception) {
            log.warn("[ERROR] WebSocket connect failed: {}", exception.getMessage());
            scheduleReconnect();
        }
    }

    private void onNewHead(NewHeadsNotification notification) {
        long headNumber = Numeric.decodeQuantity(notification.getParams().getResult().getNumber()).longValueExact();
        log.debug("[BLOCK_RECEIVED] websocket newHead number={}", headNumber);
        collectExecutor.execute(() -> collectSafely(headNumber));
    }

    private void collectSafely(long headNumber) {
        try {
            blockCollectionService.collectUpTo(headNumber);
        } catch (CollectorException exception) {
            metrics.incrementError();
            log.error("[ERROR] Collection triggered by newHead {} failed: {}", headNumber, exception.getMessage());
        }
    }

    private void onStreamError(Throwable failure) {
        if (!running) {
            return;
        }
        log.warn("[ERROR] WebSocket stream error: {}", failure.getMessage());
        closeConnection();
        scheduleReconnect();
    }

    private void onStreamCompleted() {
        if (!running) {
            return;
        }
        log.warn("WebSocket stream completed unexpectedly");
        closeConnection();
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (!running) {
            return;
        }
        CollectorProperties.Backoff policy = collectorProperties.websocketReconnect();
        int attempt = reconnectAttempts.incrementAndGet();
        if (attempt > policy.maxAttempts()) {
            log.error("[ERROR] WebSocket reconnect attempts exhausted ({}). Collector stopped; "
                    + "restart the application or switch chainwatch.collector.mode to polling.", policy.maxAttempts());
            running = false;
            return;
        }
        long delayMs = BackoffRetryExecutor.computeDelay(policy, attempt);
        metrics.incrementWebsocketReconnect();
        log.warn("[RECONNECT] attempt={}/{} delayMs={}", attempt, policy.maxAttempts(), delayMs);
        reconnectScheduler.schedule(this::connect, delayMs, TimeUnit.MILLISECONDS);
    }

    private void closeConnection() {
        Disposable currentSubscription = subscription;
        if (currentSubscription != null && !currentSubscription.isDisposed()) {
            currentSubscription.dispose();
        }
        subscription = null;

        WebSocketConnection currentConnection = connection;
        if (currentConnection != null) {
            try {
                currentConnection.close();
            } catch (RuntimeException exception) {
                log.debug("Ignoring error while closing WebSocket connection: {}", exception.getMessage());
            }
        }
        connection = null;
    }

    int currentReconnectAttempts() {
        return reconnectAttempts.get();
    }

    /** API 키가 URL 경로에 들어가는 공급자(Alchemy/Infura)를 고려해 host까지만 로그에 남긴다. */
    private String sanitizeEndpoint(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getScheme() + "://" + uri.getHost();
        } catch (RuntimeException exception) {
            return "ws-endpoint";
        }
    }

    private static Thread daemonThread(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }
}
