package com.chainwatch.backend.feed.api;

import com.chainwatch.backend.feed.service.FeedCacheService;
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

    public FeedController(FeedCacheService feedCacheService) {
        this.feedCacheService = feedCacheService;
    }

    @GetMapping("/recent-transactions")
    public List<CollectedTransactionMessage> getRecentTransactions(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return feedCacheService.getRecentTransactions(limit);
    }

    @GetMapping("/recent-events")
    public List<DetectedEventMessage> getRecentEvents(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return feedCacheService.getRecentEvents(limit);
    }
}
