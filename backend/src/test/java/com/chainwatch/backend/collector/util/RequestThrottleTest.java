package com.chainwatch.backend.collector.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RequestThrottleTest {

    @Test
    void firstRequestPassesWithoutWaiting() {
        AtomicLong clock = new AtomicLong(1_000);
        List<Long> sleeps = new ArrayList<>();
        RequestThrottle throttle = new RequestThrottle(200, sleeps::add, clock::get);

        throttle.acquire();

        assertThat(sleeps).isEmpty();
    }

    @Test
    void secondImmediateRequestWaitsForMinInterval() {
        AtomicLong clock = new AtomicLong(1_000);
        List<Long> sleeps = new ArrayList<>();
        RequestThrottle throttle = new RequestThrottle(200, sleeps::add, clock::get);

        throttle.acquire();
        throttle.acquire();

        assertThat(sleeps).containsExactly(200L);
    }

    @Test
    void requestAfterIntervalDoesNotWait() {
        AtomicLong clock = new AtomicLong(1_000);
        List<Long> sleeps = new ArrayList<>();
        RequestThrottle throttle = new RequestThrottle(200, sleeps::add, clock::get);

        throttle.acquire();
        clock.addAndGet(500);
        throttle.acquire();

        assertThat(sleeps).isEmpty();
    }

    @Test
    void disabledThrottleNeverWaits() {
        List<Long> sleeps = new ArrayList<>();
        RequestThrottle throttle = new RequestThrottle(0, sleeps::add, () -> 0);

        throttle.acquire();
        throttle.acquire();
        throttle.acquire();

        assertThat(sleeps).isEmpty();
    }
}
