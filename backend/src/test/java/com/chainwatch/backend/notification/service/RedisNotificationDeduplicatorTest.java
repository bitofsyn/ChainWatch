package com.chainwatch.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisNotificationDeduplicatorTest {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final String REDIS_KEY = "chainwatch:notification:dedupe:event:1";

    private StringRedisTemplate redisTemplate;
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private RedisNotificationDeduplicator deduplicator;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        deduplicator = new RedisNotificationDeduplicator(redisTemplate, TTL);
    }

    @Test
    void isDuplicateWhenKeyExists() {
        when(redisTemplate.hasKey(REDIS_KEY)).thenReturn(true);

        assertThat(deduplicator.isDuplicate("event:1")).isTrue();
    }

    @Test
    void isNotDuplicateWhenKeyMissing() {
        when(redisTemplate.hasKey(REDIS_KEY)).thenReturn(false);

        assertThat(deduplicator.isDuplicate("event:1")).isFalse();
    }

    @Test
    void markSentStoresKeyWithTtl() {
        deduplicator.markSent("event:1");

        verify(valueOperations).set(eq(REDIS_KEY), anyString(), eq(TTL));
    }

    @Test
    void failsOpenWhenRedisUnavailable() {
        when(redisTemplate.hasKey(anyString())).thenThrow(new QueryTimeoutException("redis down"));

        assertThat(deduplicator.isDuplicate("event:1")).isFalse();
    }

    @Test
    void markSentSwallowsRedisFailure() {
        when(redisTemplate.opsForValue()).thenThrow(new QueryTimeoutException("redis down"));

        deduplicator.markSent("event:1");
    }
}
