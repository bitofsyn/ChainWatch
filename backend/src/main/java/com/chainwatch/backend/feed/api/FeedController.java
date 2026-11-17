package com.chainwatch.backend.feed.api;

import com.chainwatch.backend.feed.service.FeedCacheService;
import com.chainwatch.backend.feed.service.RecentFeedQueryService;
import com.chainwatch.backend.ops.config.InfraProperties;
import com.chainwatch.backend.messaging.producer.CollectedTransactionMessage;
import com.chainwatch.backend.messaging.producer.DetectedEventMessage;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feed")
public class FeedController {

    private final FeedCacheService feedCacheService;
    private final RecentFeedQueryService recentFeedQueryService;
    private final boolean redisEnabled;

    public FeedController(
            FeedCacheService feedCacheService,
            RecentFeedQueryService recentFeedQueryService,
            InfraProperties infraProperties
    ) {
        this.feedCacheService = feedCacheService;
        this.recentFeedQueryService = recentFeedQueryService;
        this.redisEnabled = infraProperties.redisEnabled();
    }

    @GetMapping("/recent-transactions")
    public List<CollectedTransactionMessage> getRecentTransactions(
            @RequestParam(defaultValue = "20") int limit
    ) {
        // Redis 미사용 구성에서는 캐시가 없으므로 DB의 최신 건을 그대로 보여준다.
        return redisEnabled
                ? feedCacheService.getRecentTransactions(limit)
                : recentFeedQueryService.recentTransactions(limit);
    }

    @GetMapping("/recent-events")
    public List<DetectedEventMessage> getRecentEvents(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return redisEnabled
                ? feedCacheService.getRecentEvents(limit)
                : recentFeedQueryService.recentEvents(limit);
    }
}
