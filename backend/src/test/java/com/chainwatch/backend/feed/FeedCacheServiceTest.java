package com.chainwatch.backend.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.chainwatch.backend.feed.config.FeedCacheProperties;
import com.chainwatch.backend.feed.service.FeedCacheService;
import com.chainwatch.backend.messaging.producer.DetectedEventMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class FeedCacheServiceTest {

    private static final String EVENTS_KEY = "feed:recent-events";
    private static final int MAX_SIZE = 5;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private FeedCacheService feedCacheService;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        feedCacheService = new FeedCacheService(
                stringRedisTemplate,
                objectMapper,
                new FeedCacheProperties("feed:recent-transactions", EVENTS_KEY, MAX_SIZE)
        );
    }

    @Test
    void cacheEventRemovesExistingEntryBeforePush() throws Exception {
        DetectedEventMessage message = eventMessage(1L);
        String json = objectMapper.writeValueAsString(message);

        feedCacheService.cacheEvent(message);

        InOrder order = inOrder(listOperations);
        order.verify(listOperations).remove(EVENTS_KEY, 0, json);
        order.verify(listOperations).leftPush(EVENTS_KEY, json);
        order.verify(listOperations).trim(EVENTS_KEY, 0, MAX_SIZE - 1L);
    }

    @Test
    void getRecentEventsDeduplicatesByEventIdAndHonorsLimit() throws Exception {
        when(listOperations.range(EVENTS_KEY, 0, MAX_SIZE - 1L)).thenReturn(List.of(
                objectMapper.writeValueAsString(eventMessage(1L)),
                objectMapper.writeValueAsString(eventMessage(5L)),
                objectMapper.writeValueAsString(eventMessage(1L)),
                objectMapper.writeValueAsString(eventMessage(3L)),
                objectMapper.writeValueAsString(eventMessage(2L))
        ));

        List<DetectedEventMessage> events = feedCacheService.getRecentEvents(3);

        assertThat(events).extracting(DetectedEventMessage::eventId)
                .containsExactly(1L, 5L, 3L);
    }

    @Test
    void getRecentEventsReturnsEmptyListWhenCacheIsEmpty() {
        when(listOperations.range(anyString(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong())).thenReturn(List.of());

        assertThat(feedCacheService.getRecentEvents(10)).isEmpty();
    }

    private DetectedEventMessage eventMessage(Long eventId) {
        return new DetectedEventMessage(
                eventId, null, null, 80, "summary", "0xwallet", "0xtx",
                Instant.ofEpochSecond(1_700_000_000L)
        );
    }
}
