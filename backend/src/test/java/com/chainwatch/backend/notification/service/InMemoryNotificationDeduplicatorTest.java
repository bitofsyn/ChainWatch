package com.chainwatch.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class InMemoryNotificationDeduplicatorTest {

    private static class MutableClock extends Clock {
        private final AtomicReference<Instant> now;

        MutableClock(Instant initial) {
            this.now = new AtomicReference<>(initial);
        }

        void advance(Duration duration) {
            now.updateAndGet(instant -> instant.plus(duration));
        }

        @Override
        public Instant instant() {
            return now.get();
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    @Test
    void detectsDuplicateWithinTtl() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-05T00:00:00Z"));
        var deduplicator = new InMemoryNotificationDeduplicator(Duration.ofMinutes(30), clock);

        deduplicator.markSent("event:1");

        assertThat(deduplicator.isDuplicate("event:1")).isTrue();
        assertThat(deduplicator.isDuplicate("event:2")).isFalse();
    }

    @Test
    void expiresAfterTtl() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-05T00:00:00Z"));
        var deduplicator = new InMemoryNotificationDeduplicator(Duration.ofMinutes(30), clock);

        deduplicator.markSent("event:1");
        clock.advance(Duration.ofMinutes(31));

        assertThat(deduplicator.isDuplicate("event:1")).isFalse();
    }
}
