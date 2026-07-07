package com.chainwatch.backend.notification.service;

import com.chainwatch.backend.notification.config.NotificationProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * JVM 메모리 기반 dedupe. 단일 인스턴스 개발/테스트용이며,
 * 다중 인스턴스 운영에서는 dedup-store=redis(RedisNotificationDeduplicator)를 사용한다.
 */
@Component
@ConditionalOnProperty(prefix = "chainwatch.notification", name = "dedup-store", havingValue = "memory", matchIfMissing = true)
public class InMemoryNotificationDeduplicator implements NotificationDeduplicator {

    private final Map<String, Instant> sentAt = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public InMemoryNotificationDeduplicator(NotificationProperties properties) {
        this(Duration.ofMinutes(properties.dedupTtlMinutes()), Clock.systemUTC());
    }

    InMemoryNotificationDeduplicator(Duration ttl, Clock clock) {
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public boolean isDuplicate(String key) {
        evictExpired();
        Instant last = sentAt.get(key);
        return last != null && last.plus(ttl).isAfter(clock.instant());
    }

    @Override
    public void markSent(String key) {
        sentAt.put(key, clock.instant());
    }

    private void evictExpired() {
        Instant cutoff = clock.instant().minus(ttl);
        sentAt.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }
}
