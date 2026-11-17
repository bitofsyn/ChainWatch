package com.chainwatch.backend.feed.service;

import com.chainwatch.backend.feed.config.FeedCacheProperties;
import com.chainwatch.backend.feed.exception.FeedCacheException;
import com.chainwatch.backend.messaging.producer.CollectedTransactionMessage;
import com.chainwatch.backend.messaging.producer.DetectedEventMessage;
import com.chainwatch.backend.ops.config.InfraProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class FeedCacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final FeedCacheProperties feedCacheProperties;
    /** false면 Redis 미사용 구성: 캐시 쓰기는 건너뛰고 조회는 빈 목록을 돌려준다. */
    private final boolean redisEnabled;

    @Autowired
    public FeedCacheService(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            FeedCacheProperties feedCacheProperties,
            InfraProperties infraProperties
    ) {
        this(stringRedisTemplate, objectMapper, feedCacheProperties, infraProperties.redisEnabled());
    }

    public FeedCacheService(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            FeedCacheProperties feedCacheProperties
    ) {
        this(stringRedisTemplate, objectMapper, feedCacheProperties, true);
    }

    private FeedCacheService(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            FeedCacheProperties feedCacheProperties,
            boolean redisEnabled
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.feedCacheProperties = feedCacheProperties;
        this.redisEnabled = redisEnabled;
    }

    public void cacheTransaction(CollectedTransactionMessage message) {
        cache(feedCacheProperties.recentTransactionsKey(), message);
    }

    public void cacheEvent(DetectedEventMessage message) {
        cache(feedCacheProperties.recentEventsKey(), message);
    }

    public List<CollectedTransactionMessage> getRecentTransactions(int limit) {
        return read(feedCacheProperties.recentTransactionsKey(), limit,
                CollectedTransactionMessage.class, CollectedTransactionMessage::txHash);
    }

    public List<DetectedEventMessage> getRecentEvents(int limit) {
        return read(feedCacheProperties.recentEventsKey(), limit,
                DetectedEventMessage.class, DetectedEventMessage::eventId);
    }

    private void cache(String key, Object payload) {
        if (!redisEnabled) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            // 재수집으로 동일 메시지가 다시 컨슘되면 기존 엔트리를 제거하고 최신 위치로 올린다
            stringRedisTemplate.opsForList().remove(key, 0, json);
            stringRedisTemplate.opsForList().leftPush(key, json);
            stringRedisTemplate.opsForList().trim(key, 0, feedCacheProperties.maxSize() - 1L);
        } catch (JsonProcessingException exception) {
            throw new FeedCacheException("Failed to serialize feed payload", exception);
        }
    }

    private <T> List<T> read(String key, int limit, Class<T> type, Function<T, Object> identity) {
        if (!redisEnabled) {
            return Collections.emptyList();
        }
        int safeLimit = Math.max(1, limit);
        // 중복 제거 후에도 limit 개수를 채울 수 있도록 캐시 윈도 전체를 읽는다
        List<String> values = stringRedisTemplate.opsForList().range(key, 0, feedCacheProperties.maxSize() - 1L);
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Object> seen = new HashSet<>();
        return values.stream()
                .map(value -> readValue(value, type))
                .filter(item -> seen.add(identity.apply(item)))
                .limit(safeLimit)
                .toList();
    }

    private <T> T readValue(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new FeedCacheException("Failed to deserialize feed payload", exception);
        }
    }
}
