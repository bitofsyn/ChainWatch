import { useEffect, useState } from "react";
import { usePrefersReducedMotion } from "../hooks/usePrefersReducedMotion";
import { computeLiveStatus, type LiveStatus } from "../lib/liveStatus";

interface LiveStatusClusterProps {
  hasData: boolean;
  refreshing: boolean;
  resyncing: boolean;
  paused: boolean;
  allFailed: boolean;
  /** 하나 이상의 위젯 조회 실패 */
  anyError: boolean;
  lastSuccessAt: number | null;
  nextRefreshAt: number | null;
  onRefresh: () => void;
}

const timeFormat = new Intl.DateTimeFormat("ko-KR", {
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit"
});

/**
 * 다음 자동 갱신 countdown. 매초 대시보드 전체가 rerender되지 않도록
 * 독립 컴포넌트로 격리하고 이 안에서만 1초 타이머를 돈다.
 */
function Countdown({ target }: { target: number | null }) {
  const [remaining, setRemaining] = useState<number | null>(null);

  useEffect(() => {
    if (target == null) {
      setRemaining(null);
      return;
    }
    const tick = () => setRemaining(Math.max(0, Math.ceil((target - Date.now()) / 1000)));
    tick();
    const timer = setInterval(tick, 1_000);
    return () => clearInterval(timer);
  }, [target]);

  if (remaining == null) {
    return null;
  }
  return <span className="live-countdown">{remaining}초 후 갱신</span>;
}

/** 상태 종류(kind)가 바뀔 때만 aria-live 텍스트를 갱신해 불필요한 낭독을 막는다. */
function StatusAnnouncer({ kind, label }: { kind: string; label: string }) {
  const [announcement, setAnnouncement] = useState("");
  useEffect(() => {
    setAnnouncement(`데이터 상태 ${label}`);
    // label은 kind에 종속이므로 kind 변경 시에만 갱신된다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [kind]);
  return (
    <span className="sr-only" role="status" aria-live="polite">
      {announcement}
    </span>
  );
}

/**
 * Overview 헤더의 실시간 상태 클러스터.
 * - 상태 점 + 텍스트 + 마지막 성공 시각 (색만으로 구분하지 않음)
 * - LIVE 점은 상시 pulse하지 않고, 데이터가 실제 도착한 순간에만 1회 ripple
 * - 수동 새로고침 아이콘은 요청 진행 중에만 회전, aria-live로 결과 피드백
 */
export function LiveStatusCluster(props: LiveStatusClusterProps) {
  const reducedMotion = usePrefersReducedMotion();
  const [now, setNow] = useState(() => Date.now());

  // 상태 판정용 시계: STALE 경과 판정을 위해 15초 간격이면 충분하다.
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 15_000);
    return () => clearInterval(timer);
  }, []);

  const status: LiveStatus = computeLiveStatus({
    hasData: props.hasData,
    refreshing: props.refreshing,
    resyncing: props.resyncing,
    paused: props.paused,
    allFailed: props.allFailed,
    anyError: props.anyError,
    lastSuccessAt: props.lastSuccessAt,
    now
  });

  // navigator.onLine은 서버 상태 확정이 아니라 네트워크 힌트로만 쓴다.
  const offlineHint =
    status.kind === "OFFLINE" && typeof navigator !== "undefined" && !navigator.onLine
      ? "네트워크 연결 없음"
      : null;

  return (
    <div className={`live-cluster status-${status.kind.toLowerCase()}`}>
      <span className="live-dot-wrap" aria-hidden="true">
        <i className="live-dot" />
        {/* 갱신 성공 순간에만 1회 ripple (lastSuccessAt 변경 시 remount로 재실행) */}
        {!reducedMotion && status.kind === "LIVE" && props.lastSuccessAt != null ? (
          <i key={props.lastSuccessAt} className="live-ripple" />
        ) : null}
      </span>
      <span className="live-label">{status.label}</span>
      {status.detail || offlineHint ? (
        <span className="live-detail">{offlineHint ?? status.detail}</span>
      ) : null}
      <span className="live-updated">
        {props.lastSuccessAt != null
          ? `마지막 성공 ${timeFormat.format(props.lastSuccessAt)}`
          : "갱신 이력 없음"}
      </span>
      {status.kind === "LIVE" || status.kind === "STALE" ? (
        <Countdown target={props.nextRefreshAt} />
      ) : null}
      <span className="ops-auto-note">30초 자동 갱신</span>
      <button
        type="button"
        className="ghost-button refresh-button"
        onClick={props.onRefresh}
        disabled={props.refreshing}
      >
        <i className={`refresh-icon ${props.refreshing ? "spinning" : ""}`} aria-hidden="true" />
        {props.refreshing ? "갱신 중" : "새로고침"}
      </button>
      {/* 상태 종류가 실제로 바뀔 때만 보조기기에 알린다 (polling마다 낭독 금지) */}
      <StatusAnnouncer kind={status.kind} label={status.label} />
    </div>
  );
}
