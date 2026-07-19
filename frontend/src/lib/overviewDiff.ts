import type { OpsOverview, OpsSeriesPoint } from "../types";

/**
 * polling 전후 성공 응답을 비교하는 명시적 diff 모델.
 * - 최초 로드(previous 부재)는 변화로 취급하지 않는다(direction: "initial").
 * - null(미측정)과 0을 구분한다: null이 끼어 있으면 delta를 만들지 않는다.
 * - range 변경(query transition)은 호출부에서 previous를 null로 넘겨 initial로 처리한다.
 */

export type ChangeDirection = "up" | "down" | "same" | "initial";

export interface ValueChange {
  previous: number | null;
  current: number | null;
  delta: number | null;
  deltaPercent: number | null;
  direction: ChangeDirection;
  changedAt: number;
}

/**
 * previous === undefined 는 "비교 대상 없음(최초 로드/query transition)"을 뜻한다.
 * previous === null 은 "직전 응답에서 미측정(—)"을 뜻하며 역시 변화 애니메이션을 만들지 않는다.
 */
export function diffValue(
  previous: number | null | undefined,
  current: number | null,
  changedAt: number
): ValueChange {
  if (previous === undefined || previous === null || current === null) {
    return {
      previous: previous ?? null,
      current,
      delta: null,
      deltaPercent: null,
      direction: previous === undefined || previous === null ? "initial" : "same",
      changedAt
    };
  }
  const delta = current - previous;
  return {
    previous,
    current,
    delta,
    deltaPercent: previous === 0 ? null : (delta / previous) * 100,
    direction: delta > 0 ? "up" : delta < 0 ? "down" : "same",
    changedAt
  };
}

/** KPI별 의미 방향: 상승이 항상 긍정이 아니다. */
export type KpiSemantic = "neutral" | "higher-worse";

export type ChangeTone = "neutral" | "caution" | "improve";

/** delta chip·flash 색상 결정용. 실제 변화(up/down)에만 tone을 부여한다. */
export function changeTone(change: ValueChange, semantic: KpiSemantic): ChangeTone {
  if (change.direction !== "up" && change.direction !== "down") {
    return "neutral";
  }
  if (semantic === "neutral") {
    return "neutral";
  }
  return change.direction === "up" ? "caution" : "improve";
}

export interface OverviewKpiChanges {
  lagBlocks: ValueChange;
  transactionsPerMinute: ValueChange;
  detectionRatePercent: ValueChange;
  backlogCount: ValueChange;
}

export const KPI_SEMANTICS: Record<keyof OverviewKpiChanges, KpiSemantic> = {
  lagBlocks: "higher-worse",
  transactionsPerMinute: "neutral",
  detectionRatePercent: "higher-worse",
  backlogCount: "higher-worse"
};

/**
 * 주요 KPI만 골라 얕게 비교한다(객체 전체 deep diff 금지).
 * previous가 null이면 전부 initial — 최초 로드와 query transition 공용 경로.
 */
export function diffOverview(
  previous: OpsOverview | null,
  next: OpsOverview,
  changedAt: number
): OverviewKpiChanges {
  const prevOf = <T>(pick: (overview: OpsOverview) => T): T | undefined =>
    previous == null ? undefined : pick(previous);
  return {
    lagBlocks: diffValue(prevOf((o) => o.collector.lagBlocks), next.collector.lagBlocks, changedAt),
    transactionsPerMinute: diffValue(
      prevOf((o) => o.kpis.transactionsPerMinute),
      next.kpis.transactionsPerMinute,
      changedAt
    ),
    detectionRatePercent: diffValue(
      prevOf((o) => o.kpis.detectionRatePercent),
      next.kpis.detectionRatePercent,
      changedAt
    ),
    backlogCount: diffValue(prevOf((o) => o.kpis.backlogCount), next.kpis.backlogCount, changedAt)
  };
}

/**
 * 신규 항목 판별: 이전 성공 응답에 없던 key만 신규다.
 * previousKeys가 null이면 최초 로드이므로 아무것도 신규로 표시하지 않는다.
 */
export function findNewKeys(
  previousKeys: ReadonlySet<string> | null,
  currentKeys: readonly string[]
): Set<string> {
  if (previousKeys == null) {
    return new Set();
  }
  return new Set(currentKeys.filter((key) => !previousKeys.has(key)));
}

/** 시계열에서 실제로 새로 추가된 bucket key(bucketStart). 최초 로드는 빈 집합. */
export function newBucketKeys(
  previousSeries: OpsSeriesPoint[] | null,
  nextSeries: OpsSeriesPoint[]
): Set<string> {
  if (previousSeries == null) {
    return new Set();
  }
  const prev = new Set(previousSeries.map((point) => point.bucketStart));
  return new Set(
    nextSeries.map((point) => point.bucketStart).filter((key) => !prev.has(key))
  );
}
