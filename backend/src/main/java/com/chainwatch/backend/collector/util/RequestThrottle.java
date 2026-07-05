package com.chainwatch.backend.collector.util;

import com.chainwatch.backend.collector.exception.CollectorException;
import java.util.function.LongSupplier;

/**
 * RPC 요청 간 최소 간격을 강제해 Alchemy/Infura 등 공급자의 rate limit을 넘지 않게 한다.
 * 요청을 직렬화하는 것이 목적이므로 대기 중 락을 유지한다.
 */
public class RequestThrottle {

    private final long minIntervalMs;
    private final Sleeper sleeper;
    private final LongSupplier clockMillis;

    private long nextAllowedAtMs;

    public RequestThrottle(long minIntervalMs) {
        this(minIntervalMs, Sleeper.THREAD_SLEEP, System::currentTimeMillis);
    }

    public RequestThrottle(long minIntervalMs, Sleeper sleeper, LongSupplier clockMillis) {
        this.minIntervalMs = minIntervalMs;
        this.sleeper = sleeper;
        this.clockMillis = clockMillis;
    }

    public synchronized void acquire() {
        if (minIntervalMs <= 0) {
            return;
        }
        long now = clockMillis.getAsLong();
        long waitMs = nextAllowedAtMs - now;
        if (waitMs > 0) {
            try {
                sleeper.sleep(waitMs);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new CollectorException("Throttle wait interrupted", interrupted);
            }
            now = nextAllowedAtMs;
        }
        nextAllowedAtMs = now + minIntervalMs;
    }
}
