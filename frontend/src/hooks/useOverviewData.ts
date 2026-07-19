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
import {
  diffOverview,
  findNewKeys,
  newBucketKeys,
  type OverviewKpiChanges
} from "../lib/overviewDiff";
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

export const REFRESH_INTERVAL_MS = 30_000;
const QUEUE_FETCH_SIZE = 10;
const QUEUE_DISPLAY_SIZE = 8;
const FEED_SIZE = 6;

const EMPTY_KEYS: ReadonlySet<string> = new Set();

type LoadReason = "initial" | "auto" | "manual" | "resync" | "range";

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
  /** 탭 복귀 직후 동기화 중 */
  resyncing: boolean;
  /** 탭 비활성화로 polling 중단 */
  paused: boolean;
  /** 마지막으로 하나 이상의 위젯이 성공한 시각 */
  lastUpdated: Date | null;
  /** lastUpdated의 epoch ms (상태 계산·ripple 트리거용) */
  lastSuccessAt: number | null;
  /** 다음 자동 갱신 예정 시각(epoch ms). polling 중단 시 null */
  nextRefreshAt: number | null;
  /** 모든 위젯이 실패해 백엔드 미연결로 간주되는 상태 */
  allFailed: boolean;
  /** 직전 성공 응답과의 KPI diff. 최초 로드·range 변경 직후는 전부 initial */
  kpiChanges: OverviewKpiChanges | null;
  /** 이번 갱신에서 새로 나타난 항목 key들 (이전 성공 응답 대비) */
  newQueueIds: ReadonlySet<string>;
  newEventFeedIds: ReadonlySet<string>;
  newTransactionKeys: ReadonlySet<string>;
  /** 같은 query에서 실제로 추가된 시계열 bucket key */
  newBuckets: ReadonlySet<string>;
  refresh: () => void;
}

/**
 * Overview 대시보드 데이터 로더.
 * - 30초 자동 갱신(setTimeout 체인 → nextRefreshAt이 항상 실제 예정 시각), 탭 비활성화 시 중단
 * - 위젯별 독립 실패 처리: 실패한 위젯만 error 플래그, 기존 데이터는 지우지 않음(stale 표시)
 * - AbortController로 언마운트/재요청 시 이전 요청을 취소하고, abort는 오류로 표시하지 않음
 * - 직전 성공 응답을 보존해 KPI diff·신규 항목·신규 bucket을 계산한다.
 *   range 변경(query transition)은 diff를 만들지 않는다(전부 initial).
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
    resyncing: false,
    paused: typeof document !== "undefined" ? document.hidden : false,
    lastUpdated: null as Date | null,
    lastSuccessAt: null as number | null,
    nextRefreshAt: null as number | null,
    allFailed: false,
    kpiChanges: null as OverviewKpiChanges | null,
    newQueueIds: EMPTY_KEYS,
    newEventFeedIds: EMPTY_KEYS,
    newTransactionKeys: EMPTY_KEYS,
    newBuckets: EMPTY_KEYS
  });
  const controllerRef = useRef<AbortController | null>(null);
  const rangeRef = useRef(range);
  rangeRef.current = range;
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  /** 직전 성공 응답 스냅샷 (diff 기준) */
  const prevOverviewRef = useRef<OpsOverview | null>(null);
  const prevQueueIdsRef = useRef<ReadonlySet<string> | null>(null);
  const prevEventFeedIdsRef = useRef<ReadonlySet<string> | null>(null);
  const prevTxKeysRef = useRef<ReadonlySet<string> | null>(null);

  const clearTimer = () => {
    if (timerRef.current != null) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  };

  const load = useCallback(async (reason: LoadReason) => {
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    const activeRange = rangeRef.current;

    // setTimeout 체인: 이번 조회 시작 기준으로 다음 자동 갱신을 예약한다.
    clearTimer();
    const nextRefreshAt = document.hidden ? null : Date.now() + REFRESH_INTERVAL_MS;
    if (nextRefreshAt != null) {
      timerRef.current = setTimeout(() => load("auto"), REFRESH_INTERVAL_MS);
    }

    setState((previous) => ({
      ...previous,
      refreshing: true,
      resyncing: reason === "resync",
      paused: document.hidden,
      nextRefreshAt
    }));

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
    const now = Date.now();

    /* ── diff 계산: 실패/stale 유지 상태에서는 변화가 없다 ── */
    const overview = valueOf(overviewR);
    let kpiChanges: OverviewKpiChanges | null = null;
    let newBuckets: ReadonlySet<string> = EMPTY_KEYS;
    if (overview != null) {
      const prev = prevOverviewRef.current;
      // range/bucket이 바뀐 query transition은 실시간 변화와 구분한다.
      const sameQuery =
        prev != null && prev.range === overview.range && prev.bucket === overview.bucket;
      kpiChanges = diffOverview(sameQuery ? prev : null, overview, now);
      newBuckets = sameQuery ? newBucketKeys(prev.series, overview.series) : EMPTY_KEYS;
      prevOverviewRef.current = overview;
    }

    let newQueueIds: ReadonlySet<string> = EMPTY_KEYS;
    if (queue != null) {
      const ids = queue.map((item) => String(item.id));
      newQueueIds = findNewKeys(prevQueueIdsRef.current, ids);
      prevQueueIdsRef.current = new Set(ids);
    }
    const eventFeed = valueOf(eventFeedR);
    let newEventFeedIds: ReadonlySet<string> = EMPTY_KEYS;
    if (eventFeed != null) {
      const ids = eventFeed.map((item) => String(item.eventId));
      newEventFeedIds = findNewKeys(prevEventFeedIdsRef.current, ids);
      prevEventFeedIdsRef.current = new Set(ids);
    }
    const transactionFeed = valueOf(transactionFeedR);
    let newTransactionKeys: ReadonlySet<string> = EMPTY_KEYS;
    if (transactionFeed != null) {
      const keys = transactionFeed.map((item) => item.txHash);
      newTransactionKeys = findNewKeys(prevTxKeysRef.current, keys);
      prevTxKeysRef.current = new Set(keys);
    }

    startTransition(() => {
      setState((previous) => ({
        // 실패한 위젯은 기존 데이터를 유지(stale)하고 error 플래그만 세운다.
        overview: overview ?? previous.overview,
        overviewError: failed(overviewR),
        pipeline: valueOf(pipelineR) ?? previous.pipeline,
        pipelineError: failed(pipelineR),
        stats: valueOf(statsR) ?? previous.stats,
        statsError: failed(statsR),
        queue: queue ?? previous.queue,
        queueError: !queueLoaded,
        eventFeed: eventFeed ?? previous.eventFeed,
        transactionFeed: transactionFeed ?? previous.transactionFeed,
        feedError: failed(eventFeedR) && failed(transactionFeedR),
        loading: false,
        refreshing: false,
        resyncing: false,
        paused: document.hidden,
        lastUpdated: anySucceeded ? new Date(now) : previous.lastUpdated,
        lastSuccessAt: anySucceeded ? now : previous.lastSuccessAt,
        nextRefreshAt: previous.nextRefreshAt,
        allFailed: !anySucceeded,
        kpiChanges: kpiChanges ?? previous.kpiChanges,
        newQueueIds,
        newEventFeedIds,
        newTransactionKeys,
        newBuckets
      }));
    });
  }, []);

  // range 변경 시 즉시 재조회. 탭이 숨겨지면 다음 예약을 취소한다.
  useEffect(() => {
    const isRangeChange = prevOverviewRef.current != null;
    load(isRangeChange ? "range" : "initial");

    const onVisibilityChange = () => {
      if (document.hidden) {
        clearTimer();
        setState((previous) => ({ ...previous, paused: true, nextRefreshAt: null }));
      } else {
        // 복귀 즉시 조회하며 "복귀 후 동기화 중"을 표시한다.
        load("resync");
      }
    };

    document.addEventListener("visibilitychange", onVisibilityChange);
    return () => {
      clearTimer();
      document.removeEventListener("visibilitychange", onVisibilityChange);
      controllerRef.current?.abort();
    };
  }, [load, range]);

  const refresh = useCallback(() => {
    load("manual");
  }, [load]);

  return { ...state, refresh };
}
