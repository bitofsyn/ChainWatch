import type { DetectionEventItem } from "../types";
import { formatEventType } from "./format";

export interface EventFilters {
  eventType?: string;
  riskLevel?: string;
  /** 처리 상태 필터 (Wave1: FALSE_POSITIVE 포함, NEW는 레거시 null status 포함) */
  status?: string;
  wallet?: string;
  /** 담당자(assignee) 정확 일치 필터. unassigned가 true면 무시된다. */
  assignee?: string;
  /** 미할당 이벤트만 조회. assignee보다 우선한다. */
  unassigned?: boolean;
  /** 체인 필터 (예: ethereum-mainnet). 기본 체인 선택 시 레거시 null 행도 포함된다. */
  network?: string;
  /** datetime-local 입력값 (예: 2026-07-07T09:00). 쿼리 시 ISO Instant로 변환된다. */
  from?: string;
  to?: string;
}

export const DEFAULT_PAGE_SIZE = 20;

/** datetime-local 값을 백엔드 Instant(ISO, UTC)로 변환. 유효하지 않으면 null. */
function toIsoInstant(value: string | undefined): string | null {
  if (!value) {
    return null;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

export function buildEventsQuery(filters: EventFilters, size = DEFAULT_PAGE_SIZE, page = 0): string {
  const params = new URLSearchParams();
  if (filters.eventType) {
    params.set("eventType", filters.eventType);
  }
  if (filters.riskLevel) {
    params.set("riskLevel", filters.riskLevel);
  }
  if (filters.status) {
    params.set("status", filters.status);
  }
  if (filters.wallet && filters.wallet.trim()) {
    params.set("wallet", filters.wallet.trim());
  }
  // 미할당 필터는 담당자 필터보다 우선한다(백엔드와 동일 규칙).
  if (filters.unassigned) {
    params.set("unassigned", "true");
  } else if (filters.assignee && filters.assignee.trim()) {
    params.set("assignee", filters.assignee.trim());
  }
  if (filters.network) {
    params.set("network", filters.network);
  }
  const from = toIsoInstant(filters.from);
  if (from) {
    params.set("from", from);
  }
  const to = toIsoInstant(filters.to);
  if (to) {
    params.set("to", to);
  }
  if (page > 0) {
    params.set("page", String(page));
  }
  params.set("size", String(size));
  return params.toString();
}

/**
 * 해시 라우트 쿼리 문자열을 필터로 복원한다 (딥링크: #/events?riskLevel=HIGH&status=NEW).
 * buildEventsQuery의 역방향이며, 알 수 없는 파라미터는 무시한다.
 */
export function parseEventsQuery(query: string): EventFilters {
  const params = new URLSearchParams(query);
  const filters: EventFilters = {};
  const stringKeys = ["eventType", "riskLevel", "status", "wallet", "assignee", "network"] as const;
  for (const key of stringKeys) {
    const value = params.get(key);
    if (value) {
      filters[key] = value;
    }
  }
  if (params.get("unassigned") === "true") {
    filters.unassigned = true;
  }
  return filters;
}

export interface TrendPoint {
  bucketStart: string;
  count: number;
}

export interface EventTrend {
  hours: number;
  points: TrendPoint[];
}

export interface ChartDatum {
  key: string;
  label: string;
  count: number;
}

export function aggregateEventTypeCounts(events: DetectionEventItem[]): ChartDatum[] {
  const counts = new Map<string, number>();
  for (const event of events) {
    counts.set(event.eventType, (counts.get(event.eventType) ?? 0) + 1);
  }
  return Array.from(counts.entries())
    .map(([key, count]) => ({ key, label: formatEventType(key), count }))
    .sort((a, b) => b.count - a.count);
}
