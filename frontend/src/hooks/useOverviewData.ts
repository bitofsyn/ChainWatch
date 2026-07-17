import { startTransition, useCallback, useEffect, useRef, useState } from "react";
import {
  fetchEvents,
  fetchEventStats,
  fetchOpsOverview,
  fetchPipelineStatus,
  fetchRecentEventFeed,
  fetchRecentTransactionFeed
} from "../api";
import { sortQueue } from "../lib/opsOverview";
import type {
  DetectionEventItem,
  EventStats,
  FeedEventItem,
  FeedTransactionItem,
  OpsOverview,
  PipelineStatus
} from "../types";

export type OpsRange = "1h" | "6h" | "24h";

/** range별 버킷 크기. 백엔드 allowlist(과밀 버킷 차단)와 맞춘 조합만 사용한다. */
export const RANGE_BUCKETS: Record<OpsRange, string> = {
  "1h": "5m",
  "6h": "15m",
  "24h": "1h"
};

const REFRESH_INTERVAL_MS = 30_000;
const QUEUE_FETCH_SIZE = 10;
const QUEUE_DISPLAY_SIZE = 8;
const FEED_SIZE = 6;

export interface OverviewData {
  overview: OpsOverview | null;
  overviewError: boolean;
  pipeline: PipelineStatus | null;
  pipelineError: boolean;
  stats: EventStats | null;
  statsError: boolean;
  queue: DetectionEventItem[] | null;
  queueError: boolean;
  eventFeed: FeedEventItem[] | null;
  transactionFeed: FeedTransactionItem[] | null;
  feedError: boolean;
  /** 최초 로딩(아직 아무 데이터도 없음) */
  loading: boolean;
  /** background refresh 진행 중 (기존 데이터는 유지) */
  refreshing: boolean;
  /** 마지막으로 하나 이상의 위젯이 성공한 시각 */
  lastUpdated: Date | null;
  /** 모든 위젯이 실패해 백엔드 미연결로 간주되는 상태 */
  allFailed: boolean;
  refresh: () => void;
}

/**
 * Overview 대시보드 데이터 로더.
 * - 30초 자동 갱신, 탭 비활성화 시 polling 중단(visibilitychange)
 * - 위젯별 독립 실패 처리: 실패한 위젯만 error 플래그, 기존 데이터는 지우지 않음(stale 표시)
 * - AbortController로 언마운트/재요청 시 이전 요청을 취소하고, abort는 오류로 표시하지 않음
 */
export function useOverviewData(range: OpsRange): OverviewData {
  const [state, setState] = useState({
    overview: null as OpsOverview | null,
    overviewError: false,
    pipeline: null as PipelineStatus | null,
    pipelineError: false,
    stats: null as EventStats | null,
    statsError: false,
    queue: null as DetectionEventItem[] | null,
    queueError: false,
    eventFeed: null as FeedEventItem[] | null,
    transactionFeed: null as FeedTransactionItem[] | null,
    feedError: false,
    loading: true,
    refreshing: false,
    lastUpdated: null as Date | null,
    allFailed: false
  });
  const controllerRef = useRef<AbortController | null>(null);
  const rangeRef = useRef(range);
  rangeRef.current = range;

  const load = useCallback(async () => {
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    const activeRange = rangeRef.current;

    setState((previous) => ({ ...previous, refreshing: true }));

    const [overviewR, pipelineR, statsR, criticalR, highR, eventFeedR, transactionFeedR] =
      await Promise.allSettled([
        fetchOpsOverview(activeRange, RANGE_BUCKETS[activeRange], controller.signal),
        fetchPipelineStatus(controller.signal),
        fetchEventStats(),
        fetchEvents({ riskLevel: "CRITICAL" }, QUEUE_FETCH_SIZE),
        fetchEvents({ riskLevel: "HIGH" }, QUEUE_FETCH_SIZE),
        fetchRecentEventFeed(FEED_SIZE),
        fetchRecentTransactionFeed(FEED_SIZE)
      ]);

    // abort된 요청 결과로 화면을 갱신하지 않는다(오류 배너 금지).
    if (controller.signal.aborted) {
      return;
    }

    const valueOf = <T,>(result: PromiseSettledResult<T>): T | null =>
      result.status === "fulfilled" ? result.value : null;
    const failed = (result: PromiseSettledResult<unknown>) => result.status === "rejected";

    const critical = valueOf(criticalR)?.content ?? [];
    const high = valueOf(highR)?.content ?? [];
    const queueLoaded = !failed(criticalR) || !failed(highR);
    const queue = queueLoaded ? sortQueue([...critical, ...high]).slice(0, QUEUE_DISPLAY_SIZE) : null;

    const results = [overviewR, pipelineR, statsR, criticalR, highR, eventFeedR, transactionFeedR];
    const anySucceeded = results.some((result) => result.status === "fulfilled");

    startTransition(() => {
      setState((previous) => ({
        // 실패한 위젯은 기존 데이터를 유지(stale)하고 error 플래그만 세운다.
        overview: valueOf(overviewR) ?? previous.overview,
        overviewError: failed(overviewR),
        pipeline: valueOf(pipelineR) ?? previous.pipeline,
        pipelineError: failed(pipelineR),
        stats: valueOf(statsR) ?? previous.stats,
        statsError: failed(statsR),
        queue: queue ?? previous.queue,
        queueError: !queueLoaded,
        eventFeed: valueOf(eventFeedR) ?? previous.eventFeed,
        transactionFeed: valueOf(transactionFeedR) ?? previous.transactionFeed,
        feedError: failed(eventFeedR) && failed(transactionFeedR),
        loading: false,
        refreshing: false,
        lastUpdated: anySucceeded ? new Date() : previous.lastUpdated,
        allFailed: !anySucceeded
      }));
    });
  }, []);

  // range 변경 시 즉시 재조회 + 30초 주기 갱신. 탭이 숨겨지면 타이머를 멈춘다.
  useEffect(() => {
    let timer: ReturnType<typeof setInterval> | null = null;

    const startPolling = () => {
      if (timer == null) {
        timer = setInterval(load, REFRESH_INTERVAL_MS);
      }
    };
    const stopPolling = () => {
      if (timer != null) {
        clearInterval(timer);
        timer = null;
      }
    };
    const onVisibilityChange = () => {
      if (document.hidden) {
        stopPolling();
      } else {
        load();
        startPolling();
      }
    };

    load();
    if (!document.hidden) {
      startPolling();
    }
    document.addEventListener("visibilitychange", onVisibilityChange);
    return () => {
      stopPolling();
      document.removeEventListener("visibilitychange", onVisibilityChange);
      controllerRef.current?.abort();
    };
  }, [load, range]);

  return { ...state, refresh: load };
}
