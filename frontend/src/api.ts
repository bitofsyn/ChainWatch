import type {
  AuditLogPage,
  CollectorResult,
  CollectorState,
  DetectionEventDetail,
  DetectionEventItem,
  DetectionEventPage,
  DetectionRules,
  DetectionPolicyPatch,
  EventStats,
  EventStatusUpdateRequest,
  FeedEventItem,
  FeedTransactionItem,
  HealthResponse,
  LoginResult,
  OpsOverview,
  PipelineStatus,
  Role,
  TransactionItem,
  TransactionPage,
  User,
  UserAccountItem,
  UserCreateResult,
  WalletSummary
} from "./types";
import { buildEventsQuery, type EventFilters, type EventTrend } from "./lib/events";
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from "./lib/auth";

export class ApiError extends Error {
  constructor(public status: number, public serverMessage: string | null = null) {
    super(serverMessage ?? `API request failed: ${status}`);
  }
}

/** 세션이 완전히 만료됐을 때(리프레시 실패) AuthProvider가 수신하는 이벤트. */
export const LOGOUT_EVENT = "chainwatch:logout";

/** 동시 401 다발 시 refresh 요청이 한 번만 나가도록 하는 단일 비행 프라미스. */
let refreshInFlight: Promise<boolean> | null = null;

async function refreshOnce(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      const refreshToken = getRefreshToken();
      if (!refreshToken) {
        return false;
      }
      try {
        const response = await fetch("/api/auth/refresh", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ refreshToken })
        });
        if (!response.ok) {
          return false;
        }
        const body = (await response.json()) as { accessToken: string; refreshToken: string };
        setTokens(body.accessToken, body.refreshToken);
        return true;
      } catch {
        return false;
      }
    })().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

async function parseError(response: Response): Promise<ApiError> {
  let serverMessage: string | null = null;
  try {
    const body = (await response.json()) as { message?: string };
    serverMessage = typeof body.message === "string" ? body.message : null;
  } catch {
    serverMessage = null;
  }
  return new ApiError(response.status, serverMessage);
}

async function requestJson<T>(url: string, init: RequestInit = {}): Promise<T> {
  const doFetch = async (): Promise<Response> => {
    const headers = new Headers(init.headers);
    const token = getAccessToken();
    if (token) {
      headers.set("Authorization", `Bearer ${token}`);
    }
    return fetch(url, { ...init, headers });
  };

  let response = await doFetch();

  // 액세스 토큰 만료로 보이는 401은 리프레시 후 1회만 재시도한다.
  // 인증 엔드포인트 자체(로그인/리프레시)는 재시도 대상에서 제외.
  const isAuthEndpoint = url.startsWith("/api/auth/login") || url.startsWith("/api/auth/refresh");
  if (response.status === 401 && !isAuthEndpoint && getRefreshToken()) {
    const refreshed = await refreshOnce();
    if (refreshed) {
      response = await doFetch();
    }
  }

  if (!response.ok) {
    if (response.status === 401 && !isAuthEndpoint) {
      // 리프레시로도 복구 불가: 세션 종료를 앱 전역에 알린다
      clearTokens();
      window.dispatchEvent(new Event(LOGOUT_EVENT));
    }
    throw await parseError(response);
  }

  if (response.status === 204) {
    return undefined as T;
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

export function fetchPipelineStatus(signal?: AbortSignal) {
  return requestJson<PipelineStatus>("/api/ops/pipeline", { signal });
}

/** GET /api/ops/overview — 운영 대시보드 집계(collector lag, KPI, 시계열, 매트릭스) */
export function fetchOpsOverview(range = "24h", bucket = "1h", signal?: AbortSignal) {
  return requestJson<OpsOverview>(`/api/ops/overview?range=${range}&bucket=${bucket}`, { signal });
}

export function fetchDetectionRules() {
  return requestJson<DetectionRules>("/api/detection/rules");
}

/** PATCH /api/detection/rules/thresholds — 탐지 threshold·주소 목록 런타임 수정 (ADMIN, 부분 수정) */
export function updateDetectionThresholds(patch: DetectionPolicyPatch) {
  return requestJson<DetectionRules>("/api/detection/rules/thresholds", {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(patch)
  });
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

/** 현재 세션의 리프레시 토큰을 서버에서 폐기한다(다른 브라우저 세션은 유지). */
export function logoutSession(refreshToken: string) {
  return requestJson<void>("/api/auth/logout", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken })
  });
}

export function fetchMe() {
  return requestJson<User>("/api/auth/me");
}

export function changeMyPassword(currentPassword: string, newPassword: string) {
  return requestJson<void>("/api/auth/password", {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ currentPassword, newPassword })
  });
}

/** ── 사용자 관리 (ADMIN 전용) ───────────────── */

export function fetchUsers() {
  return requestJson<UserAccountItem[]>("/api/users");
}

export interface UserCreateInput {
  username: string;
  role: Role;
  displayName?: string;
  initialPassword?: string;
}

export function createUser(input: UserCreateInput) {
  return requestJson<UserCreateResult>("/api/users", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input)
  });
}

export interface UserUpdateInput {
  role?: Role;
  displayName?: string;
  active?: boolean;
}

export function updateUser(id: number, input: UserUpdateInput) {
  return requestJson<UserAccountItem>(`/api/users/${id}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input)
  });
}

export function resetUserPassword(id: number, newPassword?: string) {
  return requestJson<UserCreateResult>(`/api/users/${id}/password-reset`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(newPassword ? { newPassword } : {})
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
