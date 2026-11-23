package com.chainwatch.backend.event.service;

import com.chainwatch.backend.event.api.EventStatsResponse;
import com.chainwatch.backend.event.repository.DetectionEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** 대시보드 통계 집계를 짧게 캐시해 동일한 전체 집계 쿼리가 매 요청마다 반복되지 않게 한다. */
@Service
public class EventStatsService {

    private static final int TOP_WALLET_COUNT = 5;

    private final DetectionEventRepository repository;
    private final long cacheTtlNanos;
    private volatile CachedStats cached;

    public EventStatsService(
            DetectionEventRepository repository,
            @Value("${chainwatch.events.stats-cache-ttl:10s}") Duration cacheTtl
    ) {
        this.repository = repository;
        this.cacheTtlNanos = Math.max(0, cacheTtl.toNanos());
    }

    public EventStatsResponse getStats() {
        long nowNanos = System.nanoTime();
        CachedStats current = cached;
        if (current != null && nowNanos < current.expiresAtNanos()) {
            return current.response();
        }
        return refresh(nowNanos);
    }

    private synchronized EventStatsResponse refresh(long nowNanos) {
        CachedStats current = cached;
        if (current != null && nowNanos < current.expiresAtNanos()) {
            return current.response();
        }

        Instant now = Instant.now();
        long total = repository.count();
        long last24h = repository.countByDetectedAtAfter(now.minus(Duration.ofHours(24)));
        List<EventStatsResponse.KeyCount> riskLevelCounts = toKeyCounts(repository.countGroupByRiskLevel());
        List<EventStatsResponse.KeyCount> eventTypeCounts = toKeyCounts(repository.countGroupByEventType());
        List<EventStatsResponse.KeyCount> statusCounts = toStatusCounts(repository.countGroupByStatus());
        List<EventStatsResponse.WalletCount> topWallets = repository
                .findTopWalletsByEventCount(PageRequest.of(0, TOP_WALLET_COUNT))
                .stream()
                .map(row -> new EventStatsResponse.WalletCount(
                        (String) row[0],
                        (Long) row[1],
                        row[2] != null ? ((Number) row[2]).intValue() : 0,
                        (Instant) row[3]
                ))
                .toList();

        EventStatsResponse response = new EventStatsResponse(
                total, last24h, riskLevelCounts, eventTypeCounts, statusCounts, topWallets);
        cached = new CachedStats(response, nowNanos + cacheTtlNanos);
        return response;
    }

    private static List<EventStatsResponse.KeyCount> toKeyCounts(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new EventStatsResponse.KeyCount(String.valueOf(row[0]), (Long) row[1]))
                .toList();
    }

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

    private record CachedStats(EventStatsResponse response, long expiresAtNanos) {
    }
}
