import type { DetectionEventItem } from "../types";
import { formatEventType } from "./format";

export interface EventFilters {
  eventType?: string;
  riskLevel?: string;
  wallet?: string;
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
  if (filters.wallet && filters.wallet.trim()) {
    params.set("wallet", filters.wallet.trim());
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
