package com.chainwatch.backend.collector.util;

import com.chainwatch.backend.collector.config.CollectorProperties;
import com.chainwatch.backend.collector.exception.CollectorConfigurationException;
import com.chainwatch.backend.collector.exception.CollectorException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 지수 백오프 재시도 실행기. 최대 시도 횟수를 넘기면 실패를 전파한다(무한 재시도 금지).
 * 설정 오류({@link CollectorConfigurationException})는 재시도하지 않는다.
 */
public class BackoffRetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(BackoffRetryExecutor.class);

    private final CollectorProperties.Backoff policy;
    private final Sleeper sleeper;
    private final Consumer<String> retryListener;

    public BackoffRetryExecutor(CollectorProperties.Backoff policy, Sleeper sleeper, Consumer<String> retryListener) {
        this.policy = policy;
        this.sleeper = sleeper;
        this.retryListener = retryListener;
    }

    public <T> T execute(String operationName, Supplier<T> action) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            try {
                return action.get();
            } catch (CollectorConfigurationException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                lastFailure = exception;
                if (attempt == policy.maxAttempts()) {
                    break;
                }
                long delayMs = delayForAttempt(attempt);
                log.warn("[RETRY] operation={} attempt={}/{} nextDelayMs={} cause={}",
                        operationName, attempt, policy.maxAttempts(), delayMs, exception.getMessage());
                retryListener.accept(operationName);
                sleepQuietly(operationName, delayMs);
            }
        }
        throw new CollectorException(
                "Operation '%s' failed after %d attempts".formatted(operationName, policy.maxAttempts()),
                lastFailure
        );
    }

    public long delayForAttempt(int attempt) {
        return computeDelay(policy, attempt);
    }

    public static long computeDelay(CollectorProperties.Backoff policy, int attempt) {
        double delay = policy.initialDelayMs() * Math.pow(policy.multiplier(), attempt - 1L);
        return Math.min((long) delay, policy.maxDelayMs());
    }

    private void sleepQuietly(String operationName, long delayMs) {
        try {
            sleeper.sleep(delayMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CollectorException("Retry interrupted for operation '%s'".formatted(operationName), interrupted);
        }
    }
}
