package com.chainwatch.backend.event.api;

import com.chainwatch.backend.event.repository.DetectionEventRepository;
import com.chainwatch.backend.event.service.EventStatsService;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/stats")
public class EventStatsController {

    private final DetectionEventRepository detectionEventRepository;
    private final EventStatsService eventStatsService;

    public EventStatsController(
            DetectionEventRepository detectionEventRepository,
            EventStatsService eventStatsService
    ) {
        this.detectionEventRepository = detectionEventRepository;
        this.eventStatsService = eventStatsService;
    }

    @GetMapping
    public EventStatsResponse getStats() {
        return eventStatsService.getStats();
    }

    /**
     * 시간 단위 탐지 추이. 빈 시간대도 0으로 채워 차트가 연속된 축을 그릴 수 있게 한다.
     */
    @GetMapping("/trend")
    public EventTrendResponse getTrend(@RequestParam(defaultValue = "24") int hours) {
        int safeHours = Math.min(Math.max(1, hours), 168);
        Instant now = Instant.now();
        Instant since = now.minus(Duration.ofHours(safeHours - 1L)).truncatedTo(ChronoUnit.HOURS);

        Map<Instant, Long> buckets = new LinkedHashMap<>();
        for (Instant cursor = since; !cursor.isAfter(now); cursor = cursor.plus(1, ChronoUnit.HOURS)) {
            buckets.put(cursor, 0L);
        }
        for (Instant detectedAt : detectionEventRepository.findDetectedAtSince(since)) {
            buckets.merge(detectedAt.truncatedTo(ChronoUnit.HOURS), 1L, Long::sum);
        }

        List<EventTrendResponse.TrendPoint> points = buckets.entrySet().stream()
                .map(entry -> new EventTrendResponse.TrendPoint(entry.getKey(), entry.getValue()))
                .toList();
        return new EventTrendResponse(safeHours, points);
    }

}
