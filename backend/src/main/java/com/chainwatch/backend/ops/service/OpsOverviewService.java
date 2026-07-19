package com.chainwatch.backend.ops.service;

import com.chainwatch.backend.collector.service.BlockCollectionService;
import com.chainwatch.backend.collector.service.ChainFinalityService;
import com.chainwatch.backend.event.domain.EventStatus;
import com.chainwatch.backend.event.repository.DetectionEventRepository;
import com.chainwatch.backend.ops.api.OpsOverviewResponse;
import com.chainwatch.backend.ops.api.OpsOverviewResponse.CollectorHealth;
import com.chainwatch.backend.ops.api.OpsOverviewResponse.CollectorSnapshot;
import com.chainwatch.backend.ops.api.OpsOverviewResponse.EventTypeCount;
import com.chainwatch.backend.ops.api.OpsOverviewResponse.Kpis;
import com.chainwatch.backend.ops.api.OpsOverviewResponse.RiskStatusCell;
import com.chainwatch.backend.ops.api.OpsOverviewResponse.SeriesPoint;
import com.chainwatch.backend.transaction.repository.TransactionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 운영 대시보드 집계. 모든 집계는 DB group by 또는 count 쿼리로 수행하고,
 * chain head는 외부 RPC를 호출하지 않고 수집 사이클이 저장해 둔 관측치를 재사용한다.
 */
@Service
public class OpsOverviewService {

    /** DLT 카운터 이름. {@link com.chainwatch.backend.messaging.consumer.RawTransactionDltMonitor}와 동일해야 한다. */
    static final String DLT_COUNTER_NAME = "chainwatch.detection.dlt.messages";

    /** KPI 창(처리량·탐지율 계산 구간). */
    public static final Duration KPI_WINDOW = Duration.ofMinutes(5);

    /** collector 상태 판정 임계값(블록). lag ≤ UP_MAX = UP, ≤ DEGRADED_MAX = DEGRADED, 초과 = DOWN. */
    public static final long LAG_UP_MAX_BLOCKS = 30;
    public static final long LAG_DEGRADED_MAX_BLOCKS = 300;

    /** range/bucket 허용값. 과도한 범위·과밀 버킷 조회를 allowlist로 차단한다. */
    private static final Map<String, Duration> RANGES = Map.of(
            "1h", Duration.ofHours(1),
            "6h", Duration.ofHours(6),
            "24h", Duration.ofHours(24)
    );
    private static final Map<String, Duration> BUCKETS = Map.of(
            "5m", Duration.ofMinutes(5),
            "15m", Duration.ofMinutes(15),
            "30m", Duration.ofMinutes(30),
            "1h", Duration.ofHours(1)
    );
    private static final int MAX_BUCKET_COUNT = 96;

    private static final int TOP_EVENT_TYPE_COUNT = 5;

    private final TransactionRepository transactionRepository;
    private final DetectionEventRepository detectionEventRepository;
    private final BlockCollectionService blockCollectionService;
    private final ChainFinalityService chainFinalityService;
    private final MeterRegistry meterRegistry;

    public OpsOverviewService(
            TransactionRepository transactionRepository,
            DetectionEventRepository detectionEventRepository,
            BlockCollectionService blockCollectionService,
            ChainFinalityService chainFinalityService,
            MeterRegistry meterRegistry
    ) {
        this.transactionRepository = transactionRepository;
        this.detectionEventRepository = detectionEventRepository;
        this.blockCollectionService = blockCollectionService;
        this.chainFinalityService = chainFinalityService;
        this.meterRegistry = meterRegistry;
    }

    public OpsOverviewResponse overview(String rangeKey, String bucketKey) {
        Duration range = RANGES.get(rangeKey);
        if (range == null) {
            throw new IllegalArgumentException("range must be one of " + RANGES.keySet());
        }
        Duration bucket = BUCKETS.get(bucketKey);
        if (bucket == null) {
            throw new IllegalArgumentException("bucket must be one of " + BUCKETS.keySet());
        }
        long bucketCount = range.toSeconds() / bucket.toSeconds();
        if (range.toSeconds() % bucket.toSeconds() != 0 || bucketCount < 2 || bucketCount > MAX_BUCKET_COUNT) {
            throw new IllegalArgumentException(
                    "unsupported range/bucket combination: " + rangeKey + "/" + bucketKey
                            + " (bucket must divide range into 2.." + MAX_BUCKET_COUNT + " buckets)");
        }

        Instant now = Instant.now();
        return new OpsOverviewResponse(
                now,
                rangeKey,
                bucketKey,
                collectorSnapshot(),
                kpis(now),
                series(now, bucket.toSeconds(), (int) bucketCount),
                riskStatusMatrix(),
                eventTypes(now.minus(range))
        );
    }

    private CollectorSnapshot collectorSnapshot() {
        Long chainHead = chainFinalityService.lastKnownChainHead().orElse(null);
        long lastCollectedRaw = blockCollectionService.lastCollectedBlockNumber();
        Long lastCollected = lastCollectedRaw >= 0 ? lastCollectedRaw : null;
        Long lag = chainHead != null && lastCollected != null
                ? Math.max(0, chainHead - lastCollected)
                : null;
        CollectorHealth status;
        if (lag == null) {
            status = CollectorHealth.UNKNOWN;
        } else if (lag <= LAG_UP_MAX_BLOCKS) {
            status = CollectorHealth.UP;
        } else if (lag <= LAG_DEGRADED_MAX_BLOCKS) {
            status = CollectorHealth.DEGRADED;
        } else {
            status = CollectorHealth.DOWN;
        }
        return new CollectorSnapshot(
                chainHead, lastCollected, lag, chainFinalityService.confirmationDepth(), status);
    }

    private Kpis kpis(Instant now) {
        Instant windowStart = now.minus(KPI_WINDOW);
        Instant previousWindowStart = now.minus(KPI_WINDOW.multipliedBy(2));

        long collected = transactionRepository.countInWindow(windowStart, now);
        long collectedPrevious = transactionRepository.countInWindow(previousWindowStart, windowStart);
        long detected = detectionEventRepository.countDetectedInWindow(windowStart, now);

        double perMinute = collected / (double) KPI_WINDOW.toMinutes();
        // 직전 구간이 0건이면 증감률은 정의되지 않으므로 null(프론트 "—")로 내린다.
        Double deltaPercent = collectedPrevious == 0
                ? null
                : (collected - collectedPrevious) * 100.0 / collectedPrevious;
        // 분모(수집) 0이면 탐지율도 null. 0%로 거짓 표시하지 않는다.
        Double detectionRate = collected == 0 ? null : detected * 100.0 / collected;

        long backlog = detectionEventRepository.countBacklog();
        Instant oldestBacklog = detectionEventRepository.oldestBacklogDetectedAt();
        Long oldestAge = oldestBacklog == null
                ? null
                : Math.max(0, Duration.between(oldestBacklog, now).getSeconds());

        return new Kpis(perMinute, deltaPercent, detectionRate, detected, backlog, oldestAge, dltCount());
    }

    /** DLT 카운터가 등록돼 있지 않으면(Kafka 미기동 등) 0으로 속이지 않고 null을 반환한다. */
    private Long dltCount() {
        Counter counter = meterRegistry.find(DLT_COUNTER_NAME).counter();
        return counter == null ? null : (long) counter.count();
    }

    /**
     * 버킷 경계에 정렬된 시계열. 빈 버킷도 0으로 채워 차트 축이 연속되게 한다.
     * 버킷 종료 시각이 now 이후면 아직 집계 중인 부분(partial) 버킷으로 명시한다
     * — 프론트가 시계로 추정하지 않도록 계약에 포함한다.
     */
    private List<SeriesPoint> series(Instant now, long bucketSeconds, int bucketCount) {
        long currentBucketStart = Math.floorDiv(now.getEpochSecond(), bucketSeconds) * bucketSeconds;
        long firstBucketStart = currentBucketStart - (long) (bucketCount - 1) * bucketSeconds;
        Instant since = Instant.ofEpochSecond(firstBucketStart);

        Map<Long, long[]> buckets = new LinkedHashMap<>();
        for (int i = 0; i < bucketCount; i++) {
            buckets.put(firstBucketStart + (long) i * bucketSeconds, new long[2]);
        }
        mergeBucketCounts(buckets, transactionRepository.countByTimeBucketSince(since, bucketSeconds), 0);
        mergeBucketCounts(buckets, detectionEventRepository.countByTimeBucketSince(since, bucketSeconds), 1);

        List<SeriesPoint> points = new ArrayList<>(bucketCount);
        for (Map.Entry<Long, long[]> entry : buckets.entrySet()) {
            long collected = entry.getValue()[0];
            long detected = entry.getValue()[1];
            Double rate = collected == 0 ? null : detected * 100.0 / collected;
            boolean partial = entry.getKey() + bucketSeconds > now.getEpochSecond();
            points.add(new SeriesPoint(
                    Instant.ofEpochSecond(entry.getKey()), collected, detected, rate, partial));
        }
        return points;
    }

    private static void mergeBucketCounts(Map<Long, long[]> buckets, List<Object[]> rows, int index) {
        for (Object[] row : rows) {
            long bucketEpoch = ((Number) row[0]).longValue();
            long[] slot = buckets.get(bucketEpoch);
            if (slot != null) {
                slot[index] = ((Number) row[1]).longValue();
            }
        }
    }

    /** 레거시 null status는 NEW로 합산해 프론트가 별도 규칙을 갖지 않게 한다. */
    private List<RiskStatusCell> riskStatusMatrix() {
        Map<String, Long> merged = new LinkedHashMap<>();
        for (Object[] row : detectionEventRepository.countGroupByRiskLevelAndStatus()) {
            String riskLevel = String.valueOf(row[0]);
            String status = row[1] == null ? EventStatus.NEW.name() : String.valueOf(row[1]);
            merged.merge(riskLevel + "|" + status, (Long) row[2], Long::sum);
        }
        return merged.entrySet().stream()
                .map(entry -> {
                    String[] key = entry.getKey().split("\\|", 2);
                    return new RiskStatusCell(key[0], key[1], entry.getValue());
                })
                .toList();
    }

    private List<EventTypeCount> eventTypes(Instant since) {
        return detectionEventRepository.countGroupByEventTypeSince(since).stream()
                .limit(TOP_EVENT_TYPE_COUNT)
                .map(row -> new EventTypeCount(String.valueOf(row[0]), (Long) row[1]))
                .toList();
    }
}
