import type {
  AuditLogPage,
  CollectorResult,
  CollectorState,
  DetectionEventDetail,
  DetectionEventItem,
  DetectionEventPage,
  DetectionRules,
  EventStats,
  EventStatusUpdateRequest,
  FeedEventItem,
  FeedTransactionItem,
  HealthResponse,
  LoginResult,
  PipelineStatus,
  TransactionItem,
  TransactionPage,
  WalletSummary
} from "./types";
import { buildEventsQuery, type EventFilters, type EventTrend } from "./lib/events";
import { clearToken, getToken } from "./lib/auth";

export class ApiError extends Error {
  constructor(public status: number, public serverMessage: string | null = null) {
    super(serverMessage ?? `API request failed: ${status}`);
  }
}

async function requestJson<T>(url: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  const token = getToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(url, { ...init, headers });
  if (!response.ok) {
    if (response.status === 401) {
      clearToken();
    }
    let serverMessage: string | null = null;
    try {
      const body = (await response.json()) as { message?: string };
      serverMessage = typeof body.message === "string" ? body.message : null;
    } catch {
      serverMessage = null;
    }
    throw new ApiError(response.status, serverMessage);
  }

  return response.json() as Promise<T>;
}

export function fetchHealth() {
  return requestJson<HealthResponse>("/api/health");
}

export function fetchEvents(filters: EventFilters = {}, size = 20, page = 0) {
  return requestJson<DetectionEventPage>(`/api/events?${buildEventsQuery(filters, size, page)}`);
}

export function fetchEventDetail(id: number) {
  return requestJson<DetectionEventDetail>(`/api/events/${id}`);
}

export function fetchEventStats() {
  return requestJson<EventStats>("/api/events/stats");
}

export function fetchEventTrend(hours = 24) {
  return requestJson<EventTrend>(`/api/events/stats/trend?hours=${hours}`);
}

export function updateEventStatus(id: number, request: EventStatusUpdateRequest) {
  return requestJson<DetectionEventItem>(`/api/events/${id}/status`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request)
  });
}

export interface AuditLogFilters {
  actor?: string;
  action?: string;
}

/** GET /api/audit-logs — ADMIN 전용, 403이면 ApiError(403) */
export function fetchAuditLogs(filters: AuditLogFilters = {}, size = 20, page = 0) {
  const params = new URLSearchParams({ size: String(size) });
  if (filters.actor && filters.actor.trim()) {
    params.set("actor", filters.actor.trim());
  }
  if (filters.action && filters.action.trim()) {
    params.set("action", filters.action.trim());
  }
  if (page > 0) {
    params.set("page", String(page));
  }
  return requestJson<AuditLogPage>(`/api/audit-logs?${params}`);
}

export function fetchTransaction(id: number) {
  return requestJson<TransactionItem>(`/api/transactions/${id}`);
}

export function fetchTransactions(wallet: string, size = 10, page = 0) {
  const params = new URLSearchParams({ wallet, size: String(size) });
  if (page > 0) {
    params.set("page", String(page));
  }
  return requestJson<TransactionPage>(`/api/transactions?${params}`);
}

export function fetchWalletSummary(address: string) {
  return requestJson<WalletSummary>(`/api/wallets/${encodeURIComponent(address)}`);
}

export function fetchPipelineStatus() {
  return requestJson<PipelineStatus>("/api/ops/pipeline");
}

export function fetchDetectionRules() {
  return requestJson<DetectionRules>("/api/detection/rules");
}

export function fetchRecentEventFeed(limit = 5) {
  return requestJson<FeedEventItem[]>(`/api/feed/recent-events?limit=${limit}`);
}

export function fetchRecentTransactionFeed(limit = 5) {
  return requestJson<FeedTransactionItem[]>(`/api/feed/recent-transactions?limit=${limit}`);
}

export function login(username: string, password: string) {
  return requestJson<LoginResult>("/api/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password })
  });
}

export function requestAnalysis(eventId: number) {
  return requestJson<DetectionEventDetail["aiReport"]>(`/api/events/${eventId}/analysis`, {
    method: "POST"
  });
}

export function fetchCollectorState() {
  return requestJson<CollectorState>("/api/collector/state");
}

export function collectLatestBlock() {
  return requestJson<CollectorResult>("/api/collector/blocks/latest", { method: "POST" });
}

export function collectBlock(blockNumber: number) {
  return requestJson<CollectorResult>(`/api/collector/blocks/${blockNumber}`, { method: "POST" });
}
