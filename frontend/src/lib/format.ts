import type { EventStatus } from "../types";

export const CRITICAL_THRESHOLD = 85;
export const HIGH_THRESHOLD = 70;

export const EVENT_TYPE_LABELS: Record<string, string> = {
  LARGE_TRANSFER: "대규모 이체",
  EXCHANGE_FLOW: "거래소 입출금",
  RAPID_TRANSFER: "반복 이체",
  WATCHLIST_ACTIVITY: "관심 지갑 활동"
};

export const RISK_LEVEL_LABELS: Record<string, string> = {
  LOW: "낮음",
  MEDIUM: "중간",
  HIGH: "높음",
  CRITICAL: "치명"
};

export function toStatus(riskScore: number): EventStatus {
  if (riskScore >= CRITICAL_THRESHOLD) {
    return "critical";
  }
  if (riskScore >= HIGH_THRESHOLD) {
    return "high";
  }
  return "elevated";
}

export function formatEventType(value: string): string {
  return EVENT_TYPE_LABELS[value] ?? value;
}

export function formatDate(value: string): string {
  return new Intl.DateTimeFormat("ko-KR", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}

export function shortenAddress(value: string, head = 8, tail = 6): string {
  if (value.length <= head + tail + 3) {
    return value;
  }
  return `${value.slice(0, head)}...${value.slice(-tail)}`;
}
