package com.chainwatch.backend.ops.api;

import java.time.Instant;
import java.util.List;

/**
 * GET /api/ops/overview 응답. 운영 대시보드가 한 번의 호출로 파이프라인 상태를
 * 판단할 수 있도록 collector lag, 처리량 KPI, 시계열, 위험도×상태 매트릭스를 묶어 내려준다.
 *
 * <p>측정하지 못한 값은 0으로 거짓 표시하지 않고 null을 내려 프론트가 "—"로 구분해 그리게 한다.
 */
public record OpsOverviewResponse(
        Instant generatedAt,
        String range,
        String bucket,
        CollectorSnapshot collector,
        Kpis kpis,
        List<SeriesPoint> series,
        List<RiskStatusCell> riskStatusMatrix,
        List<EventTypeCount> eventTypes
) {

    /**
     * 수집 진행도. chainHead는 외부 RPC 실시간 호출이 아니라 수집 사이클이 관측해 둔
     * 마지막 head(collector_state.last_known_chain_head)를 사용한다.
     */
    public record CollectorSnapshot(
            Long chainHead,           // null = head 미관측(수집이 아직 돌지 않음)
            Long lastCollectedBlock,  // null = 수집 이력 없음
            Long lagBlocks,           // null = 판정 불가(head 또는 수집 이력 없음)
            int confirmationDepth,
            CollectorHealth status
    ) {
    }

    public enum CollectorHealth { UP, DEGRADED, DOWN, UNKNOWN }

    public record Kpis(
            double transactionsPerMinute,
            Double transactionsDeltaPercent,  // null = 직전 구간 0건이라 비교 불가
            Double detectionRatePercent,      // null = 최근 창 수집 0건(분모 0)
            long detectedLast5m,
            long backlogCount,                // NEW(레거시 null 포함) + ACKNOWLEDGED
            Long oldestBacklogAgeSeconds,     // null = backlog 없음
            Long dltCount                     // null = 측정 불가(카운터 미등록). 값은 프로세스 기동 이후 누적
    ) {
    }

    public record SeriesPoint(
            Instant bucketStart,
            long collectedTransactions,
            long detectedEvents,
            Double detectionRatePercent       // null = 해당 버킷 수집 0건
    ) {
    }

    /** 위험도×처리상태 매트릭스 셀. 레거시 null status는 NEW로 합산되어 내려간다. */
    public record RiskStatusCell(String riskLevel, String status, long count) {
    }

    public record EventTypeCount(String key, long count) {
    }
}
