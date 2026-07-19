import {
  useCallback,
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent,
  type PointerEvent as ReactPointerEvent
} from "react";
import type { OpsSeriesPoint } from "../types";
import type { SurgeInsight, SurgeKind } from "../lib/opsOverview";
import { formatCompact, formatPercent } from "../lib/opsOverview";
import {
  bucketPartial,
  computeScales,
  gappedMonotonePath,
  monotonePath,
  nearestBucketIndex,
  parseBucketMs,
  type LinePoint
} from "../lib/chartGeometry";
import { useMeasuredWidth } from "../hooks/useMeasuredWidth";
import { usePrefersReducedMotion } from "../hooks/usePrefersReducedMotion";
import { useSeriesTransition } from "../hooks/useSeriesTransition";
import { ChartLegend, SERIES_ORDER, type SeriesKey } from "./ChartLegend";
import { ChartTooltip } from "./ChartTooltip";

interface TimeSeriesChartProps {
  series: OpsSeriesPoint[];
  /** 버킷 크기(partial bucket 판별·라벨 밀도, 예: "1h") */
  bucket: string;
  ariaLabel: string;
  /** range 식별자. 변경 시 morph 대신 crossfade로 전환한다(query transition). */
  queryKey: string;
  /** 서버 집계 시각. partial bucket 판별 기준(프론트 시계 대신 서버 시각 우선). */
  generatedAt?: string | null;
  /** throughputInsight와 동일한 source의 급증 구간 (marker 표시) */
  anomaly?: SurgeInsight | null;
  /** 이번 갱신에서 실제로 추가된 bucket key (진입 애니메이션 대상) */
  newBuckets?: ReadonlySet<string>;
  /** range 변경 조회 진행 중 (기존 차트 유지 + 작은 overlay) */
  updating?: boolean;
}

/* 폭은 컨테이너 실측(1:1 px) — 고정 viewBox 확대로 축 글자가 화면 폭에 따라
   뻥튀기되지 않게 한다. 높이는 관제 밀도를 위해 고정. */
const FALLBACK_WIDTH = 720;
const MIN_WIDTH = 320;
const HEIGHT = 260;
const PADS = { padLeft: 46, padRight: 44, padTop: 18, padBottom: 30 };

const timeFormat = new Intl.DateTimeFormat("ko-KR", { hour: "2-digit", minute: "2-digit" });
const dateTimeFormat = new Intl.DateTimeFormat("ko-KR", {
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit"
});
const tooltipTimeFormat = dateTimeFormat;

const ANOMALY_LABELS: Record<SurgeKind, string> = {
  "catch-up": "수집·탐지 동반 급증",
  "rule-anomaly": "탐지만 급증",
  "collect-only": "수집만 급증"
};

/** 자정(날짜 경계) 버킷은 날짜를 함께 표시한다. */
function xAxisLabel(iso: string): string {
  const date = new Date(iso);
  return date.getHours() === 0 && date.getMinutes() === 0
    ? dateTimeFormat.format(date)
    : timeFormat.format(date);
}

/**
 * 수집(막대) · 탐지(실선) · 탐지율(우측 축 점선) 관제 시계열.
 * - polling 갱신은 bucket key 기준 morph, range 변경은 crossfade (전체 fade 재실행 금지)
 * - 차트 전체가 하나의 focusable composite: ←/→/Home/End로 bucket 이동, Esc로 해제
 * - hover/tap/scrub 시 nearest bucket crosshair + unified tooltip
 * - 마지막 미완료 버킷은 "집계 중"으로 구분하고, 탐지율 null 버킷은 선을 잇지 않는다
 * - 스크린리더용으로 전체 데이터를 담은 시각적 숨김 테이블을 제공한다
 */
export function TimeSeriesChart({
  series,
  bucket,
  ariaLabel,
  queryKey,
  generatedAt,
  anomaly,
  newBuckets,
  updating
}: TimeSeriesChartProps) {
  const reducedMotion = usePrefersReducedMotion();
  const display = useSeriesTransition(series, queryKey, reducedMotion);
  // 여러 차트 인스턴스에서도 gradient id가 충돌하지 않게 한다 (useId의 콜론은 url()에 부적합해 제거).
  const gradientBase = useId().replace(/[^a-zA-Z0-9_-]/g, "");
  const barGradientId = `ts-bar-grad-${gradientBase}`;
  const areaGradientId = `ts-area-grad-${gradientBase}`;
  const [hoverIndex, setHoverIndex] = useState<number | null>(null);
  /** 고정 선택은 index가 아니라 bucket key로 저장해 시계열 이동에도 유지한다. */
  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const [hidden, setHidden] = useState<ReadonlySet<SeriesKey>>(new Set());
  const containerRef = useRef<HTMLDivElement | null>(null);
  const svgRef = useRef<SVGSVGElement | null>(null);
  const { ref: measureRef, width: measuredWidth } = useMeasuredWidth(FALLBACK_WIDTH);
  // containerRef(외부 클릭 판정)와 폭 측정을 하나의 callback ref로 묶는다.
  const setContainerRef = useCallback(
    (node: HTMLDivElement | null) => {
      containerRef.current = node;
      measureRef(node);
    },
    [measureRef]
  );
  const width = Math.max(MIN_WIDTH, measuredWidth);
  const frame = useMemo(() => ({ width, height: HEIGHT, ...PADS }), [width]);
  /** anomaly marker 최초 발견 1회 강조용 (pulse 반복 금지) */
  const seenAnomaliesRef = useRef<Set<string>>(new Set());
  /** 최초 로드 여부: 막대 grow 애니메이션은 최초 로드와 실제 신규 bucket에만 (range 변경은 crossfade만) */
  const everRenderedRef = useRef(false);

  const isInitialRender = !everRenderedRef.current;
  useEffect(() => {
    everRenderedRef.current = true;
  }, []);

  // 스케일은 최종(target) 시리즈 기준으로 고정해 morph 중 축 라벨이 흔들리지 않게 한다.
  // 숨긴 시리즈는 스케일 계산에서 제외해 남은 시리즈가 정확히 읽히게 한다.
  const scales = useMemo(() => {
    const forScale = series.map((point) => ({
      ...point,
      collectedTransactions: hidden.has("collected") ? 0 : point.collectedTransactions,
      detectedEvents: hidden.has("detected") ? 0 : point.detectedEvents
    }));
    return computeScales(forScale, frame);
  }, [series, hidden, frame]);

  const bucketMs = parseBucketMs(bucket);
  const nowMs = generatedAt ? new Date(generatedAt).getTime() : Date.now();

  const selectedIndex = useMemo(() => {
    if (selectedKey == null) {
      return null;
    }
    const index = series.findIndex((point) => point.bucketStart === selectedKey);
    return index >= 0 ? index : null;
  }, [series, selectedKey]);

  // 외부 click으로 고정 선택 해제
  useEffect(() => {
    if (selectedKey == null) {
      return;
    }
    const onDocPointerDown = (event: globalThis.PointerEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setSelectedKey(null);
      }
    };
    document.addEventListener("pointerdown", onDocPointerDown);
    return () => document.removeEventListener("pointerdown", onDocPointerDown);
  }, [selectedKey]);

  if (series.length === 0) {
    return <div className="empty-state">표시할 시계열 데이터가 없습니다.</div>;
  }

  const { maxCount, maxRate, slot, x, yCount, yRate } = scales;
  const baseline = HEIGHT - frame.padBottom;

  const toSvgX = (clientX: number): number | null => {
    const svg = svgRef.current;
    if (!svg) {
      return null;
    }
    const rect = svg.getBoundingClientRect();
    if (rect.width === 0) {
      return null;
    }
    return ((clientX - rect.left) / rect.width) * width;
  };

  const pointFromEvent = (event: ReactPointerEvent): number | null => {
    const svgX = toSvgX(event.clientX);
    return svgX == null ? null : nearestBucketIndex(svgX, series.length, frame);
  };

  const onPointerMove = (event: ReactPointerEvent) => {
    const index = pointFromEvent(event);
    if (event.pointerType === "touch") {
      // 모바일: tap 후 좌우 scrub으로 값 확인 (수직 스크롤은 touch-action: pan-y로 유지)
      if (event.buttons > 0 && index != null) {
        setSelectedKey(series[index].bucketStart);
      }
      return;
    }
    setHoverIndex(index);
  };

  const onPointerDown = (event: ReactPointerEvent) => {
    const index = pointFromEvent(event);
    if (index == null) {
      return;
    }
    const key = series[index].bucketStart;
    // 같은 bucket 재클릭은 해제, 그 외에는 고정 선택
    setSelectedKey((current) => (current === key ? null : key));
  };

  const moveSelection = (delta: number) => {
    const base = selectedIndex ?? hoverIndex ?? series.length - 1;
    const next = Math.min(series.length - 1, Math.max(0, base + delta));
    setSelectedKey(series[next].bucketStart);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    switch (event.key) {
      case "ArrowLeft":
        event.preventDefault();
        moveSelection(selectedIndex == null && hoverIndex == null ? 0 : -1);
        break;
      case "ArrowRight":
        event.preventDefault();
        moveSelection(1);
        break;
      case "Home":
        event.preventDefault();
        setSelectedKey(series[0].bucketStart);
        break;
      case "End":
        event.preventDefault();
        setSelectedKey(series[series.length - 1].bucketStart);
        break;
      case "Escape":
        setSelectedKey(null);
        setHoverIndex(null);
        break;
      default:
        break;
    }
  };

  const toggleSeries = (key: SeriesKey) => {
    setHidden((current) => {
      const next = new Set(current);
      if (next.has(key)) {
        next.delete(key);
      } else if (next.size < SERIES_ORDER.length - 1) {
        // 최소 한 series는 항상 유지한다.
        next.add(key);
      }
      return next;
    });
  };

  const activeIndex = hoverIndex ?? selectedIndex;
  const activePoint =
    activeIndex != null && activeIndex < display.length ? display[activeIndex] : null;

  const detectedPoints: LinePoint[] = display.map((point, index) => ({
    x: x(index) + slot / 2,
    y: yCount(point.detectedEvents)
  }));
  const ratePoints: (LinePoint | null)[] = display.map((point, index) =>
    point.detectionRatePercent == null
      ? null
      : { x: x(index) + slot / 2, y: yRate(point.detectionRatePercent) }
  );

  // 라벨 밀도는 실제 렌더 폭 기준(라벨당 ~110px)으로 조절한다.
  const maxLabels = Math.max(3, Math.floor(scales.innerWidth / 110));
  const labelEvery = Math.max(1, Math.ceil(series.length / maxLabels));

  const anomalyIndex =
    anomaly != null ? series.findIndex((point) => point.bucketStart === anomaly.bucketStart) : -1;
  const anomalyIsNew =
    anomaly != null && anomalyIndex >= 0 && !seenAnomaliesRef.current.has(anomaly.bucketStart);
  if (anomaly != null && anomalyIndex >= 0) {
    seenAnomaliesRef.current.add(anomaly.bucketStart);
  }

  const selectedPoint = selectedIndex != null ? series[selectedIndex] : null;

  return (
    <div
      ref={setContainerRef}
      className="ts-chart"
      role="group"
      tabIndex={0}
      aria-label={`${ariaLabel}. 버킷 ${bucket}, 총 ${series.length}개 구간. 좌우 방향키로 구간 이동, Esc로 해제`}
      onKeyDown={onKeyDown}
    >
      <svg
        ref={svgRef}
        viewBox={`0 0 ${width} ${HEIGHT}`}
        aria-hidden="true"
        style={{ touchAction: "pan-y" }}
        onMouseLeave={() => setHoverIndex(null)}
        onPointerMove={onPointerMove}
        onPointerDown={onPointerDown}
      >
        <defs>
          {/* 시리즈 토큰 기반 gradient — 색은 styles.css의 --series-* 변수만 사용 */}
          <linearGradient id={barGradientId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" style={{ stopColor: "var(--series-collected)" }} stopOpacity="0.75" />
            <stop offset="100%" style={{ stopColor: "var(--series-collected)" }} stopOpacity="0.28" />
          </linearGradient>
          <linearGradient id={areaGradientId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" style={{ stopColor: "var(--series-detected)" }} stopOpacity="0.22" />
            <stop offset="100%" style={{ stopColor: "var(--series-detected)" }} stopOpacity="0" />
          </linearGradient>
        </defs>

        {/* 축 단위 라벨 */}
        <text x={frame.padLeft - 6} y={10} textAnchor="end" className="ts-axis-text unit">
          건수
        </text>
        <text x={width - frame.padRight + 6} y={10} textAnchor="start" className="ts-axis-text rate unit">
          %
        </text>

        {/* y축 눈금 (수집/탐지 건수) — 0 baseline 고정 */}
        {[0, 0.5, 1].map((ratio) => {
          const value = Math.round(maxCount * ratio);
          const y = yCount(value);
          return (
            <g key={ratio}>
              <line x1={frame.padLeft} x2={width - frame.padRight} y1={y} y2={y} className="ts-gridline" />
              <text x={frame.padLeft - 6} y={y + 3.5} textAnchor="end" className="ts-axis-text">
                {formatCompact(value)}
              </text>
            </g>
          );
        })}
        {/* 우측 보조축 (탐지율 %) */}
        {!hidden.has("rate")
          ? [0, 1].map((ratio) => (
              <text
                key={`rate-${ratio}`}
                x={width - frame.padRight + 6}
                y={yRate(maxRate * ratio) + 3.5}
                textAnchor="start"
                className="ts-axis-text rate"
              >
                {Math.round(maxRate * ratio)}%
              </text>
            ))
          : null}

        {/* plot 본체: queryKey 변경 시 remount + crossfade (전체 fade 반복 금지) */}
        <g key={queryKey} className="ts-plot">
          {/* 수집 트랜잭션: 막대 */}
          {!hidden.has("collected")
            ? display.map((point, index) => {
                const barWidth = Math.max(slot * 0.55, 1.5);
                const barX = x(index) + (slot - barWidth) / 2;
                const barY = yCount(point.collectedTransactions);
                const partial = bucketPartial(point, bucketMs, nowMs);
                const entering =
                  !reducedMotion &&
                  (isInitialRender || (newBuckets?.has(point.bucketStart) ?? false));
                return (
                  <rect
                    key={`bar-${point.bucketStart}`}
                    x={barX}
                    y={barY}
                    width={barWidth}
                    height={Math.max(baseline - barY, point.collectedTransactions > 0 ? 1.5 : 0)}
                    rx={Math.min(2, barWidth / 2)}
                    fill={partial ? undefined : `url(#${barGradientId})`}
                    className={`ts-bar ${activeIndex === index ? "active" : ""} ${
                      partial ? "partial" : ""
                    } ${entering ? "enter" : ""}`}
                  />
                );
              })
            : null}

          {/* 탐지 이벤트: monotone 곡선 + 은은한 area gradient + hover point */}
          {!hidden.has("detected") && detectedPoints.length > 0 ? (
            <>
              <path
                d={`${monotonePath(detectedPoints)} L${detectedPoints[detectedPoints.length - 1].x.toFixed(1)},${baseline} L${detectedPoints[0].x.toFixed(1)},${baseline} Z`}
                fill={`url(#${areaGradientId})`}
                className="ts-area detected"
              />
              <path d={monotonePath(detectedPoints)} className="ts-line detected" />
              {activeIndex != null && detectedPoints[activeIndex] ? (
                <circle
                  cx={detectedPoints[activeIndex].x}
                  cy={detectedPoints[activeIndex].y}
                  r={4}
                  className="ts-point detected"
                />
              ) : null}
            </>
          ) : null}

          {/* 탐지율: 점선 monotone 곡선(보조축). null 버킷은 잇지 않고 gap */}
          {!hidden.has("rate") ? (
            <>
              <path d={gappedMonotonePath(ratePoints)} className="ts-line rate" />
              {activeIndex != null && ratePoints[activeIndex] ? (
                <circle
                  cx={ratePoints[activeIndex].x}
                  cy={ratePoints[activeIndex].y}
                  r={3}
                  className="ts-point rate"
                />
              ) : null}
            </>
          ) : null}

          {/* 이상 징후 marker: insight와 동일 bucket, 유형별 다른 모양. pulse 금지, 최초 1회만 강조 */}
          {anomaly != null && anomalyIndex >= 0 ? (
            <g
              className={`ts-anomaly kind-${anomaly.kind} ${
                anomalyIsNew && !reducedMotion ? "enter" : ""
              }`}
              transform={`translate(${x(anomalyIndex) + slot / 2}, ${frame.padTop - 6})`}
            >
              {anomaly.kind === "catch-up" ? (
                <path d="M0,-4 L4,2 L-4,2 Z M0,2 L4,8 L-4,8 Z" />
              ) : anomaly.kind === "rule-anomaly" ? (
                <path d="M0,-3 L5,6 L-5,6 Z" />
              ) : (
                <circle r={4} cy={2} />
              )}
              <title>{ANOMALY_LABELS[anomaly.kind]}</title>
            </g>
          ) : null}
        </g>

        {/* crosshair */}
        {activeIndex != null ? (
          <line
            x1={x(activeIndex) + slot / 2}
            x2={x(activeIndex) + slot / 2}
            y1={frame.padTop}
            y2={baseline}
            className={`ts-cursor ${selectedIndex === activeIndex ? "pinned" : ""}`}
          />
        ) : null}

        {/* x축 라벨 (자정 경계는 날짜 포함) */}
        {series.map((point, index) =>
          index % labelEvery === 0 ? (
            <text
              key={`label-${point.bucketStart}`}
              x={x(index) + slot / 2}
              y={HEIGHT - 8}
              textAnchor="middle"
              className="ts-axis-text"
            >
              {xAxisLabel(point.bucketStart)}
            </text>
          ) : null
        )}

        {/* 마지막 미완료 버킷 표시 */}
        {bucketPartial(series[series.length - 1], bucketMs, nowMs) ? (
          <text
            x={x(series.length - 1) + slot / 2}
            y={frame.padTop - 6}
            textAnchor="middle"
            className="ts-axis-text partial-label"
          >
            집계 중
          </text>
        ) : null}
      </svg>

      {activePoint && activeIndex != null ? (
        <ChartTooltip
          point={activePoint}
          index={activeIndex}
          seriesLength={series.length}
          hidden={hidden}
          partial={bucketPartial(series[activeIndex], bucketMs, nowMs)}
          pinned={selectedIndex === activeIndex}
        />
      ) : null}

      {updating ? (
        <div className="ts-updating-overlay" role="status">
          불러오는 중
        </div>
      ) : null}

      <ChartLegend hidden={hidden} onToggle={toggleSeries} />

      {/* 키보드/스크린리더용: 선택 구간의 최종 값만 알린다 */}
      <span className="sr-only" role="status" aria-live="polite">
        {selectedPoint
          ? `${tooltipTimeFormat.format(new Date(selectedPoint.bucketStart))} 구간 선택: ` +
            `수집 ${formatCompact(selectedPoint.collectedTransactions)}건, ` +
            `탐지 ${formatCompact(selectedPoint.detectedEvents)}건, ` +
            `탐지율 ${formatPercent(selectedPoint.detectionRatePercent)}`
          : ""}
      </span>

      {/* 차트 대체 데이터 테이블 (시각적 숨김) */}
      <table className="sr-only">
        <caption>{ariaLabel} 데이터 테이블</caption>
        <thead>
          <tr>
            <th scope="col">구간</th>
            <th scope="col">수집(건)</th>
            <th scope="col">탐지(건)</th>
            <th scope="col">탐지율(%)</th>
          </tr>
        </thead>
        <tbody>
          {series.map((point) => (
            <tr key={point.bucketStart}>
              <th scope="row">{tooltipTimeFormat.format(new Date(point.bucketStart))}</th>
              <td>{point.collectedTransactions}</td>
              <td>{point.detectedEvents}</td>
              <td>{point.detectionRatePercent == null ? "측정 없음" : formatPercent(point.detectionRatePercent)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
