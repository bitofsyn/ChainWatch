package com.chainwatch.backend.feed.service;

import com.chainwatch.backend.feed.config.FeedCacheProperties;
import com.chainwatch.backend.feed.exception.FeedCacheException;
import com.chainwatch.backend.messaging.producer.CollectedTransactionMessage;
import com.chainwatch.backend.messaging.producer.DetectedEventMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class FeedCacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final FeedCacheProperties feedCacheProperties;

    public FeedCacheService(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            FeedCacheProperties feedCacheProperties
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.feedCacheProperties = feedCacheProperties;
    }

    public void cacheTransaction(CollectedTransactionMessage message) {
        cache(feedCacheProperties.recentTransactionsKey(), message);
    }

    public void cacheEvent(DetectedEventMessage message) {
        cache(feedCacheProperties.recentEventsKey(), message);
    }

    public List<CollectedTransactionMessage> getRecentTransactions(int limit) {
        return read(feedCacheProperties.recentTransactionsKey(), limit, CollectedTransactionMessage.class);
    }

    public List<DetectedEventMessage> getRecentEvents(int limit) {
        return read(feedCacheProperties.recentEventsKey(), limit, DetectedEventMessage.class);
    }

    private void cache(String key, Object payload) {
        try {
            stringRedisTemplate.opsForList().leftPush(key, objectMapper.writeValueAsString(payload));
            stringRedisTemplate.opsForList().trim(key, 0, feedCacheProperties.maxSize() - 1L);
        } catch (JsonProcessingException exception) {
            throw new FeedCacheException("Failed to serialize feed payload", exception);
        }
    }

    private <T> List<T> read(String key, int limit, Class<T> type) {
        int safeLimit = Math.max(1, limit);
        List<String> values = stringRedisTemplate.opsForList().range(key, 0, safeLimit - 1L);
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        return values.stream()
                .map(value -> readValue(value, type))
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
