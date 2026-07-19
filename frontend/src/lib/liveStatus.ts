/**
 * Overview 헤더의 실시간 연결 상태 모델.
 * polling 결과·탭 가시성·마지막 성공 시각만으로 결정하는 순수 함수로 분리해
 * 컴포넌트와 무관하게 테스트한다. navigator.onLine은 힌트로만 쓰고
 * 서버 상태 확정에는 사용하지 않는다(호출부 책임).
 */

export type LiveStatusKind = "LIVE" | "UPDATING" | "STALE" | "PAUSED" | "OFFLINE";

/** 마지막 성공 후 이 시간이 지나면 STALE (polling 3회 분량) */
export const STALE_AFTER_MS = 90_000;

export interface LiveStatusInput {
  /** 하나 이상의 위젯 데이터 보유 여부 */
  hasData: boolean;
  /** background refresh 진행 중 */
  refreshing: boolean;
  /** 탭 복귀 직후 동기화 중 */
  resyncing: boolean;
  /** 탭 비활성화로 polling 중단 */
  paused: boolean;
  /** 마지막 시도에서 모든 API 실패 */
  allFailed: boolean;
  /** 마지막 시도에서 일부 위젯 실패 */
  anyError: boolean;
  lastSuccessAt: number | null;
  now: number;
  staleAfterMs?: number;
}

export interface LiveStatus {
  kind: LiveStatusKind;
  label: string;
  /** 상태 점 옆 보조 설명 (없으면 null) */
  detail: string | null;
}

const LABELS: Record<LiveStatusKind, string> = {
  LIVE: "LIVE",
  UPDATING: "갱신 중",
  STALE: "STALE",
  PAUSED: "일시 중지",
  OFFLINE: "연결 끊김"
};

export function computeLiveStatus(input: LiveStatusInput): LiveStatus {
  const staleAfter = input.staleAfterMs ?? STALE_AFTER_MS;
  const age = input.lastSuccessAt == null ? null : input.now - input.lastSuccessAt;

  if (input.paused) {
    return { kind: "PAUSED", label: LABELS.PAUSED, detail: "탭 비활성화로 자동 갱신 중단" };
  }
  if (input.allFailed && !input.refreshing) {
    return { kind: "OFFLINE", label: LABELS.OFFLINE, detail: "모든 API 조회 실패" };
  }
  if (input.refreshing) {
    return {
      kind: "UPDATING",
      label: LABELS.UPDATING,
      detail: input.resyncing ? "복귀 후 동기화 중" : input.hasData ? "기존 데이터 유지" : null
    };
  }
  if (input.anyError || (age != null && age > staleAfter)) {
    return { kind: "STALE", label: LABELS.STALE, detail: "최신 조회 실패 · 마지막 성공 데이터 표시" };
  }
  if (input.hasData) {
    return { kind: "LIVE", label: LABELS.LIVE, detail: null };
  }
  // 데이터도 오류 이력도 없는 초기 상태는 갱신 중으로 본다.
  return { kind: "UPDATING", label: LABELS.UPDATING, detail: null };
}
