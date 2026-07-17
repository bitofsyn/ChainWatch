import type {
  DetectionEventItem,
  OpsCollector,
  OpsCollectorHealth,
  OpsKpis,
  OpsSeriesPoint,
  PipelineComponent
} from "../types";

/* ── KPI 임계값 (백엔드 OpsOverviewService의 lag 기준과 동일) ── */

/** Collector lag: 이 값 이하 정상, 초과~DANGER 이하 주의, 초과 시 위험 (블록 수) */
export const LAG_WARN_BLOCKS = 30;
export const LAG_DANGER_BLOCKS = 300;
/** 대응 backlog(NEW+ACKNOWLEDGED) 건수 기준 */
export const BACKLOG_WARN = 20;
export const BACKLOG_DANGER = 100;
/** 가장 오래 대기한 이벤트 age 기준 (초) */
export const OLDEST_WARN_SECONDS = 30 * 60;
export const OLDEST_DANGER_SECONDS = 2 * 60 * 60;
/** 급증 판정: peak 버킷이 나머지 버킷 중앙값의 이 배수 이상 + 절대 최소 건수 */
export const SURGE_FACTOR = 3;
export const SURGE_MIN_COUNT = 10;

export type KpiLevel = "ok" | "warn" | "danger" | "unknown";

export const KPI_LEVEL_LABELS: Record<KpiLevel, string> = {
  ok: "정상",
  warn: "주의",
  danger: "위험",
  unknown: "—"
};

export function lagLevel(lagBlocks: number | null): KpiLevel {
  if (lagBlocks == null) {
    return "unknown";
  }
  if (lagBlocks <= LAG_WARN_BLOCKS) {
    return "ok";
  }
  return lagBlocks <= LAG_DANGER_BLOCKS ? "warn" : "danger";
}

export function backlogLevel(count: number, oldestAgeSeconds: number | null): KpiLevel {
  const countLevel: KpiLevel =
    count > BACKLOG_DANGER ? "danger" : count > BACKLOG_WARN ? "warn" : "ok";
  const ageLevel: KpiLevel =
    oldestAgeSeconds == null
      ? "ok"
      : oldestAgeSeconds > OLDEST_DANGER_SECONDS
        ? "danger"
        : oldestAgeSeconds > OLDEST_WARN_SECONDS
          ? "warn"
          : "ok";
  const order: KpiLevel[] = ["ok", "warn", "danger"];
  return order[Math.max(order.indexOf(countLevel), order.indexOf(ageLevel))];
}

export function collectorLevel(status: OpsCollectorHealth): KpiLevel {
  switch (status) {
    case "UP":
      return "ok";
    case "DEGRADED":
      return "warn";
    case "DOWN":
      return "danger";
    default:
      return "unknown";
  }
}

/* ── 전체 상태 (정상/주의/장애) ───────────────── */

export interface OverallStatus {
  level: "ok" | "warn" | "danger" | "unknown";
  label: string;
}

/** 파이프라인 중단으로 간주하는 핵심 컴포넌트 */
const CORE_COMPONENTS = new Set(["database", "kafka", "collector"]);

/**
 * 색상만으로 의미를 전달하지 않도록 텍스트 라벨을 함께 반환한다.
 * 장애: 핵심 컴포넌트 DOWN 또는 collector lag 위험 수준.
 * 주의: 그 외 컴포넌트 DOWN, collector DEGRADED, backlog 주의 이상.
 */
export function overallStatus(
  components: PipelineComponent[] | null,
  collector: OpsCollector | null,
  kpis: OpsKpis | null
): OverallStatus {
  if (!components && !collector && !kpis) {
    return { level: "unknown", label: "확인 불가" };
  }
  const downComponents = (components ?? []).filter((item) => item.status === "DOWN");
  const coreDown = downComponents.some((item) => CORE_COMPONENTS.has(item.name));
  if (coreDown || (collector && collector.status === "DOWN")) {
    return { level: "danger", label: "장애" };
  }
  const backlog = kpis ? backlogLevel(kpis.backlogCount, kpis.oldestBacklogAgeSeconds) : "ok";
  if (
    downComponents.length > 0 ||
    (collector && collector.status === "DEGRADED") ||
    backlog !== "ok" ||
    (kpis?.dltCount != null && kpis.dltCount > 0)
  ) {
    return { level: "warn", label: "주의" };
  }
  return { level: "ok", label: "정상" };
}

/* ── 결정론적 급증 insight ────────────────────── */

export type SurgeKind = "catch-up" | "rule-anomaly" | "collect-only";

export interface SurgeInsight {
  kind: SurgeKind;
  bucketStart: string;
}

function median(values: number[]): number {
  if (values.length === 0) {
    return 0;
  }
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 0 ? (sorted[mid - 1] + sorted[mid]) / 2 : sorted[mid];
}

function isSurge(peak: number, rest: number[]): boolean {
  return peak >= SURGE_MIN_COUNT && peak >= SURGE_FACTOR * Math.max(median(rest), 1);
}

/**
 * 급증 구간 판정. AI 추론 없이 고정 규칙만 사용한다:
 * 수집·탐지 동반 급증 → collector catch-up 가능성,
 * 탐지만 급증 → 룰 이상 발화 또는 실제 이상거래 가능성,
 * 수집만 급증 → 유입 증가(탐지율 유지 여부 확인 필요).
 * 마지막 버킷은 진행 중(부분 집계)이라 판정에서 제외한다.
 */
export function throughputInsight(series: OpsSeriesPoint[]): SurgeInsight | null {
  const complete = series.slice(0, -1);
  if (complete.length < 4) {
    return null;
  }
  let peakIndex = 0;
  for (let i = 1; i < complete.length; i++) {
    const current = complete[i].collectedTransactions + complete[i].detectedEvents;
    const peak = complete[peakIndex].collectedTransactions + complete[peakIndex].detectedEvents;
    if (current > peak) {
      peakIndex = i;
    }
  }
  const rest = complete.filter((_, index) => index !== peakIndex);
  const collectedSurge = isSurge(
    complete[peakIndex].collectedTransactions,
    rest.map((point) => point.collectedTransactions)
  );
  const detectedSurge = isSurge(
    complete[peakIndex].detectedEvents,
    rest.map((point) => point.detectedEvents)
  );
  if (collectedSurge && detectedSurge) {
    return { kind: "catch-up", bucketStart: complete[peakIndex].bucketStart };
  }
  if (detectedSurge) {
    return { kind: "rule-anomaly", bucketStart: complete[peakIndex].bucketStart };
  }
  if (collectedSurge) {
    return { kind: "collect-only", bucketStart: complete[peakIndex].bucketStart };
  }
  return null;
}

export const SURGE_MESSAGES: Record<SurgeKind, string> = {
  "catch-up":
    "수집과 탐지가 함께 급증했습니다. collector catch-up(밀린 블록 일괄 수집) 구간일 가능성이 높습니다.",
  "rule-anomaly":
    "탐지만 급증했습니다. 특정 룰의 이상 발화 또는 실제 이상거래 급증 가능성을 확인하세요.",
  "collect-only": "수집만 급증했습니다. 해당 구간의 탐지율이 유지되는지 확인하세요."
};

/* ── 조사 큐 정렬 ─────────────────────────────── */

/** CRITICAL 우선 → riskScore 내림차순 → detectedAt 오름차순(오래 대기한 것 우선) */
export function sortQueue(events: DetectionEventItem[]): DetectionEventItem[] {
  return [...events].sort((a, b) => {
    const aCritical = a.riskLevel === "CRITICAL" ? 0 : 1;
    const bCritical = b.riskLevel === "CRITICAL" ? 0 : 1;
    if (aCritical !== bCritical) {
      return aCritical - bCritical;
    }
    if (a.riskScore !== b.riskScore) {
      return b.riskScore - a.riskScore;
    }
    return a.detectedAt.localeCompare(b.detectedAt);
  });
}

/* ── 포맷터 ───────────────────────────────────── */

const compactFormat = new Intl.NumberFormat("ko-KR", {
  notation: "compact",
  maximumFractionDigits: 1
});
const numberFormat = new Intl.NumberFormat("ko-KR");

/** 축·KPI용 축약 숫자. null/undefined는 "—" */
export function formatCompact(value: number | null | undefined): string {
  return value == null ? "—" : compactFormat.format(value);
}

export function formatNumber(value: number | null | undefined): string {
  return value == null ? "—" : numberFormat.format(value);
}

export function formatPercent(value: number | null | undefined, digits = 1): string {
  return value == null ? "—" : `${value.toFixed(digits)}%`;
}

/** 증감률. +/− 부호를 명시하고 null은 "—" */
export function formatDelta(value: number | null | undefined): string {
  if (value == null) {
    return "—";
  }
  const sign = value > 0 ? "+" : value < 0 ? "−" : "±";
  return `${sign}${Math.abs(value).toFixed(1)}%`;
}

/** 대기 시간 등 초 단위 age를 짧게. null은 "—" */
export function formatAge(seconds: number | null | undefined): string {
  if (seconds == null) {
    return "—";
  }
  if (seconds < 60) {
    return `${Math.floor(seconds)}초`;
  }
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return `${minutes}분`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    const restMinutes = minutes % 60;
    return restMinutes > 0 ? `${hours}시간 ${restMinutes}분` : `${hours}시간`;
  }
  const days = Math.floor(hours / 24);
  const restHours = hours % 24;
  return restHours > 0 ? `${days}일 ${restHours}시간` : `${days}일`;
}

/** ISO 시각으로부터 현재까지의 대기 시간 */
export function ageFrom(iso: string, now: Date = new Date()): number {
  return Math.max(0, Math.floor((now.getTime() - new Date(iso).getTime()) / 1000));
}
