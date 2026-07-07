package com.chainwatch.backend.notification.service;

import com.chainwatch.backend.notification.config.NotificationProperties;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis TTL 키 기반 알림 중복 제거.
 * 인스턴스가 여러 대여도 동일 이벤트가 한 번만 알림되고, 재시작해도 dedupe 상태가 유지된다.
 * Redis 장애 시에는 fail-open(중복 아님으로 간주)하여 알림 발송 자체를 막지 않는다.
 */
@Component
@ConditionalOnProperty(prefix = "chainwatch.notification", name = "dedup-store", havingValue = "redis")
public class RedisNotificationDeduplicator implements NotificationDeduplicator {

    private static final Logger log = LoggerFactory.getLogger(RedisNotificationDeduplicator.class);
    private static final String KEY_PREFIX = "chainwatch:notification:dedupe:";

    private final StringRedisTemplate stringRedisTemplate;
    private final Duration ttl;

    @Autowired
    public RedisNotificationDeduplicator(StringRedisTemplate stringRedisTemplate, NotificationProperties properties) {
        this(stringRedisTemplate, Duration.ofMinutes(properties.dedupTtlMinutes()));
    }

    RedisNotificationDeduplicator(StringRedisTemplate stringRedisTemplate, Duration ttl) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.ttl = ttl;
    }

    @Override
    public boolean isDuplicate(String key) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(redisKey(key)));
        } catch (DataAccessException exception) {
            log.warn("notification dedupe check failed, treating as not duplicate | key={} error={}",
                    key, exception.getMessage());
            return false;
        }
    }

    @Override
    public void markSent(String key) {
        try {
            stringRedisTemplate.opsForValue().set(redisKey(key), Instant.now().toString(), ttl);
        } catch (DataAccessException exception) {
            log.warn("notification dedupe mark failed | key={} error={}", key, exception.getMessage());
        }
    }

    private String redisKey(String key) {
        return KEY_PREFIX + key;
    }
}
