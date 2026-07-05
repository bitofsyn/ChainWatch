import type { DetectionEventItem } from "../types";
import { formatEventType } from "./format";

export interface EventFilters {
  eventType?: string;
  riskLevel?: string;
  wallet?: string;
}

export const DEFAULT_PAGE_SIZE = 20;

export function buildEventsQuery(filters: EventFilters, size = DEFAULT_PAGE_SIZE): string {
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
  params.set("size", String(size));
  return params.toString();
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
