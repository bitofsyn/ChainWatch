import type {
  AgentHandoffEvent,
  AgentHandoffResult,
  AgentOpsSnapshot,
  AgentTaskOutcome,
  AgentTeam,
  AgentTeamStatus,
  SubAgentState
} from "../types";
import { buildAgentOpsSnapshot } from "../mocks/agentOps";

async function requestJson<T>(url: string): Promise<T> {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`agent-ops API request failed: ${response.status}`);
  }
  return response.json() as Promise<T>;
}

/**
 * 백엔드 GET /api/agent-ops/snapshot 을 우선 호출하고,
 * 미배포/미연결 환경에서는 mock 스냅샷으로 폴백한다 (source 필드로 구분).
 */
export async function fetchAgentOpsSnapshot(): Promise<AgentOpsSnapshot> {
  try {
    const snapshot = await requestJson<AgentOpsSnapshot>("/api/agent-ops/snapshot");
    return { ...snapshot, source: "api" };
  } catch {
    return { ...buildAgentOpsSnapshot(), source: "mock" };
  }
}

export async function fetchAgentTeam(teamId: string): Promise<AgentTeam | null> {
  const snapshot = await fetchAgentOpsSnapshot();
  return snapshot.teams.find((team) => team.id === teamId) ?? null;
}

export async function fetchAgentHandoffs(limit = 50): Promise<AgentHandoffEvent[]> {
  const snapshot = await fetchAgentOpsSnapshot();
  return snapshot.handoffs.slice(0, limit);
}

export const TEAM_STATUS_LABELS: Record<AgentTeamStatus, string> = {
  healthy: "정상",
  degraded: "성능 저하",
  blocked: "차단"
};

export const TASK_OUTCOME_LABELS: Record<AgentTaskOutcome, string> = {
  success: "성공",
  failed: "실패",
  retrying: "재시도",
  in_progress: "진행 중"
};

export const HANDOFF_RESULT_LABELS: Record<AgentHandoffResult, string> = {
  accepted: "수락",
  queued: "대기열 등록",
  rejected: "반려",
  completed: "처리 완료"
};

export const SUBAGENT_STATE_LABELS: Record<SubAgentState, string> = {
  idle: "대기",
  working: "작업 중",
  error: "오류"
};

export function formatDurationMs(ms: number): string {
  if (ms <= 0) {
    return "-";
  }
  if (ms < 1000) {
    return `${ms}ms`;
  }
  if (ms < 60_000) {
    return `${(ms / 1000).toFixed(1)}초`;
  }
  const minutes = Math.floor(ms / 60_000);
  const seconds = Math.round((ms % 60_000) / 1000);
  return seconds > 0 ? `${minutes}분 ${seconds}초` : `${minutes}분`;
}

export function formatWaitSeconds(seconds: number): string {
  if (seconds < 60) {
    return `${seconds}초`;
  }
  return formatDurationMs(seconds * 1000);
}

export function teamNameOf(teams: AgentTeam[], teamId: string | null): string {
  if (teamId == null) {
    return "-";
  }
  return teams.find((team) => team.id === teamId)?.name ?? teamId;
}

export function successRateClass(rate: number): string {
  if (rate < 70) {
    return "bad";
  }
  if (rate < 95) {
    return "warn";
  }
  return "";
}
