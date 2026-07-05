package com.chainwatch.backend.collector.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chainwatch.backend.collector.config.CollectorProperties;
import com.chainwatch.backend.collector.exception.CollectorConfigurationException;
import com.chainwatch.backend.collector.exception.CollectorException;
import com.chainwatch.backend.collector.exception.RpcClientException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BackoffRetryExecutorTest {

    private final CollectorProperties.Backoff policy = new CollectorProperties.Backoff(4, 500, 2.0, 3_000);
    private final List<Long> recordedDelays = new ArrayList<>();
    private final List<String> retriedOperations = new ArrayList<>();
    private final BackoffRetryExecutor executor =
            new BackoffRetryExecutor(policy, recordedDelays::add, retriedOperations::add);

    @Test
    void returnsResultAfterTransientFailures() {
        AtomicInteger calls = new AtomicInteger();

        String result = executor.execute("op", () -> {
            if (calls.incrementAndGet() < 3) {
                throw new RpcClientException("transient");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3);
        assertThat(recordedDelays).containsExactly(500L, 1000L);
        assertThat(retriedOperations).containsExactly("op", "op");
    }

    @Test
    void failsAfterMaxAttemptsWithoutInfiniteRetry() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> executor.execute("op", () -> {
            calls.incrementAndGet();
            throw new RpcClientException("always failing");
        }))
                .isInstanceOf(CollectorException.class)
                .hasMessageContaining("failed after 4 attempts")
                .hasCauseInstanceOf(RpcClientException.class);

        assertThat(calls.get()).isEqualTo(4);
        assertThat(recordedDelays).containsExactly(500L, 1000L, 2000L);
    }

    @Test
    void capsDelayAtMaxDelay() {
        assertThat(BackoffRetryExecutor.computeDelay(policy, 1)).isEqualTo(500L);
        assertThat(BackoffRetryExecutor.computeDelay(policy, 3)).isEqualTo(2_000L);
        assertThat(BackoffRetryExecutor.computeDelay(policy, 10)).isEqualTo(3_000L);
    }

    @Test
    void doesNotRetryConfigurationErrors() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> executor.execute("op", () -> {
            calls.incrementAndGet();
            throw new CollectorConfigurationException("misconfigured");
        }))
                .isInstanceOf(CollectorConfigurationException.class);

        assertThat(calls.get()).isEqualTo(1);
        assertThat(recordedDelays).isEmpty();
    }
}
