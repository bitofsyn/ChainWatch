package com.chainwatch.backend.event.api;

import com.chainwatch.backend.event.repository.DetectionEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/stats")
public class EventStatsController {

    private static final int TOP_WALLET_COUNT = 5;

    private final DetectionEventRepository detectionEventRepository;

    public EventStatsController(DetectionEventRepository detectionEventRepository) {
        this.detectionEventRepository = detectionEventRepository;
    }

    @GetMapping
    public EventStatsResponse getStats() {
        Instant now = Instant.now();
        long total = detectionEventRepository.count();
        long last24h = detectionEventRepository.countByDetectedAtAfter(now.minus(Duration.ofHours(24)));

        List<EventStatsResponse.KeyCount> riskLevelCounts =
                toKeyCounts(detectionEventRepository.countGroupByRiskLevel());
        List<EventStatsResponse.KeyCount> eventTypeCounts =
                toKeyCounts(detectionEventRepository.countGroupByEventType());
        List<EventStatsResponse.KeyCount> statusCounts =
                toStatusCounts(detectionEventRepository.countGroupByStatus());

        List<EventStatsResponse.WalletCount> topWallets = detectionEventRepository
                .findTopWalletsByEventCount(PageRequest.of(0, TOP_WALLET_COUNT))
                .stream()
                .map(row -> new EventStatsResponse.WalletCount(
                        (String) row[0],
                        (Long) row[1],
                        row[2] != null ? ((Number) row[2]).intValue() : 0,
                        (Instant) row[3]
                ))
                .toList();

        return new EventStatsResponse(total, last24h, riskLevelCounts, eventTypeCounts, statusCounts, topWallets);
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

    private static List<EventStatsResponse.KeyCount> toKeyCounts(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new EventStatsResponse.KeyCount(String.valueOf(row[0]), (Long) row[1]))
                .toList();
    }

    /** status 컬럼은 nullable이므로 null 그룹은 NEW로 합산한다. */
    private static List<EventStatsResponse.KeyCount> toStatusCounts(List<Object[]> rows) {
        long newCount = 0;
        java.util.Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (Object[] row : rows) {
            if (row[0] == null) {
                newCount += (Long) row[1];
            } else {
                counts.merge(String.valueOf(row[0]), (Long) row[1], Long::sum);
            }
        }
        if (newCount > 0) {
            counts.merge("NEW", newCount, Long::sum);
        }
        return counts.entrySet().stream()
                .map(entry -> new EventStatsResponse.KeyCount(entry.getKey(), entry.getValue()))
                .toList();
    }
}
