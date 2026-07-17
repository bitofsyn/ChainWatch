import type { EventLifecycleStatus, EventStatus } from "../types";

export const CRITICAL_THRESHOLD = 85;
export const HIGH_THRESHOLD = 70;

export const EVENT_TYPE_LABELS: Record<string, string> = {
  LARGE_TRANSFER: "대규모 이체",
  WHALE_ACTIVITY: "관심 지갑 활동",
  EXCHANGE_FLOW: "거래소 입출금",
  RAPID_TRANSFER: "반복 이체",
  WATCHLIST_MATCH: "watchlist 일치",
  FAN_OUT: "자금 분산"
};

export const RISK_LEVEL_LABELS: Record<string, string> = {
  LOW: "낮음",
  MEDIUM: "중간",
  HIGH: "높음",
  CRITICAL: "치명"
};

export const LIFECYCLE_STATUS_LABELS: Record<EventLifecycleStatus, string> = {
  NEW: "신규",
  ACKNOWLEDGED: "접수",
  INVESTIGATING: "조사중",
  RESOLVED: "해결",
  FALSE_POSITIVE: "오탐 종결"
};

export const LIFECYCLE_STATUS_ORDER: EventLifecycleStatus[] = [
  "NEW",
  "ACKNOWLEDGED",
  "INVESTIGATING",
  "RESOLVED",
  "FALSE_POSITIVE"
];

/** 지원 체인 라벨(짧은 표기). 필터 옵션과 배지에 공용으로 쓴다. */
export const NETWORK_LABELS: Record<string, string> = {
  "ethereum-mainnet": "Ethereum",
  "polygon-mainnet": "Polygon",
  "arbitrum-mainnet": "Arbitrum"
};

/** 필터 셀렉트에 노출할 체인 목록(현재 수집 대상 + 데이터 모델이 지원하는 체인). */
export const NETWORK_ORDER: string[] = [
  "ethereum-mainnet",
  "polygon-mainnet",
  "arbitrum-mainnet"
];

export function formatNetwork(value: string | null | undefined): string {
  if (!value) {
    return NETWORK_LABELS["ethereum-mainnet"];
  }
  return NETWORK_LABELS[value] ?? value;
}

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

export function formatLifecycleStatus(value: string): string {
  return LIFECYCLE_STATUS_LABELS[value as EventLifecycleStatus] ?? value;
}

export function formatDate(value: string): string {
  return new Intl.DateTimeFormat("ko-KR", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}

/** 연도까지 포함한 전체 시각 (감사 로그, 상태 변경 시각 등 정확성이 필요한 곳) */
export function formatFullDate(value: string): string {
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit"
  }).format(new Date(value));
}

/** 코인 금액 포맷 (소수점 뒤 불필요한 0 제거, 최대 6자리) */
export function formatAmount(value: number, unit = "ETH"): string {
  const formatted = new Intl.NumberFormat("en-US", {
    maximumFractionDigits: 6
  }).format(value);
  return `${formatted} ${unit}`;
}

export function shortenAddress(value: string, head = 8, tail = 6): string {
  if (value.length <= head + tail + 3) {
    return value;
  }
  return `${value.slice(0, head)}...${value.slice(-tail)}`;
}

/** 해시/주소가 없을 때 표시할 값 */
export function orDash(value: string | null | undefined): string {
  return value && value.trim() ? value : "-";
}
