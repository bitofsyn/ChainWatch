import { useMemo, useState } from "react";
import type { OpsSeriesPoint } from "../types";
import { formatCompact, formatPercent } from "../lib/opsOverview";

interface TimeSeriesChartProps {
  series: OpsSeriesPoint[];
  /** 버킷 크기(라벨 밀도 결정용, 예: "1h") */
  bucket: string;
  ariaLabel: string;
}

const WIDTH = 720;
const HEIGHT = 240;
const PAD_LEFT = 46;
const PAD_RIGHT = 44;
const PAD_TOP = 14;
const PAD_BOTTOM = 28;

const timeFormat = new Intl.DateTimeFormat("ko-KR", { hour: "2-digit", minute: "2-digit" });
const tooltipTimeFormat = new Intl.DateTimeFormat("ko-KR", {
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit"
});

/**
 * 수집 트랜잭션(막대) vs 탐지 이벤트(선) 비교 시계열.
 * 탐지율은 우측 보조축의 점선으로 그린다. hover/keyboard focus 모두 tooltip을 지원하고,
 * 각 버킷 rect에 aria-label을 부여해 스크린리더가 값을 읽을 수 있게 한다.
 */
export function TimeSeriesChart({ series, bucket, ariaLabel }: TimeSeriesChartProps) {
  const [activeIndex, setActiveIndex] = useState<number | null>(null);

  const layout = useMemo(() => {
    const innerWidth = WIDTH - PAD_LEFT - PAD_RIGHT;
    const innerHeight = HEIGHT - PAD_TOP - PAD_BOTTOM;
    const maxCount = Math.max(
      1,
      ...series.map((point) => Math.max(point.collectedTransactions, point.detectedEvents))
    );
    const maxRate = Math.max(
      10,
      ...series.map((point) => point.detectionRatePercent ?? 0)
    );
    const slot = series.length > 0 ? innerWidth / series.length : innerWidth;
    const x = (index: number) => PAD_LEFT + slot * index;
    const yCount = (value: number) => PAD_TOP + innerHeight * (1 - value / maxCount);
    const yRate = (value: number) => PAD_TOP + innerHeight * (1 - value / maxRate);
    return { innerWidth, innerHeight, maxCount, maxRate, slot, x, yCount, yRate };
  }, [series]);

  if (series.length === 0) {
    return <div className="empty-state">표시할 시계열 데이터가 없습니다.</div>;
  }

  const { maxCount, maxRate, slot, x, yCount, yRate } = layout;
  const baseline = HEIGHT - PAD_BOTTOM;

  const detectedPath = series
    .map((point, index) => {
      const cx = x(index) + slot / 2;
      return `${index === 0 ? "M" : "L"}${cx.toFixed(1)},${yCount(point.detectedEvents).toFixed(1)}`;
    })
    .join(" ");

  const ratePoints = series
    .map((point, index) =>
      point.detectionRatePercent == null
        ? null
        : { x: x(index) + slot / 2, y: yRate(point.detectionRatePercent) }
    )
    .filter((point): point is { x: number; y: number } => point != null);
  const ratePath = ratePoints
    .map((point, index) => `${index === 0 ? "M" : "L"}${point.x.toFixed(1)},${point.y.toFixed(1)}`)
    .join(" ");

  const labelEvery = Math.max(1, Math.ceil(series.length / 6));
  const active = activeIndex != null ? series[activeIndex] : null;

  const bucketAria = (point: OpsSeriesPoint) =>
    `${tooltipTimeFormat.format(new Date(point.bucketStart))} 구간: 수집 ${point.collectedTransactions}건, ` +
    `탐지 ${point.detectedEvents}건, 탐지율 ${formatPercent(point.detectionRatePercent)}`;

  return (
    <div className="ts-chart">
      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        role="img"
        aria-label={`${ariaLabel}. 버킷 ${bucket}, 총 ${series.length}개 구간`}
        onMouseLeave={() => setActiveIndex(null)}
      >
        {/* y축 눈금 (수집/탐지 건수) */}
        {[0, 0.5, 1].map((ratio) => {
          const value = Math.round(maxCount * ratio);
          const y = yCount(value);
          return (
            <g key={ratio}>
              <line
                x1={PAD_LEFT}
                x2={WIDTH - PAD_RIGHT}
                y1={y}
                y2={y}
                className="ts-gridline"
              />
              <text x={PAD_LEFT - 6} y={y + 3.5} textAnchor="end" className="ts-axis-text">
                {formatCompact(value)}
              </text>
            </g>
          );
        })}
        {/* 우측 보조축 (탐지율 %) */}
        {[0, 1].map((ratio) => (
          <text
            key={`rate-${ratio}`}
            x={WIDTH - PAD_RIGHT + 6}
            y={yRate(maxRate * ratio) + 3.5}
            textAnchor="start"
            className="ts-axis-text rate"
          >
            {Math.round(maxRate * ratio)}%
          </text>
        ))}

        {/* 수집 트랜잭션: 막대 */}
        {series.map((point, index) => {
          const barWidth = Math.max(slot * 0.55, 1.5);
          const barX = x(index) + (slot - barWidth) / 2;
          const barY = yCount(point.collectedTransactions);
          return (
            <rect
              key={`bar-${point.bucketStart}`}
              x={barX}
              y={barY}
              width={barWidth}
              height={Math.max(baseline - barY, point.collectedTransactions > 0 ? 1.5 : 0)}
              className={`ts-bar ${activeIndex === index ? "active" : ""}`}
            />
          );
        })}

        {/* 탐지 이벤트: 실선 */}
        <path d={detectedPath} className="ts-line detected" />
        {/* 탐지율: 점선(보조축) */}
        {ratePath ? <path d={ratePath} className="ts-line rate" /> : null}

        {/* 활성 구간 표시 */}
        {activeIndex != null ? (
          <line
            x1={x(activeIndex) + slot / 2}
            x2={x(activeIndex) + slot / 2}
            y1={PAD_TOP}
            y2={baseline}
            className="ts-cursor"
          />
        ) : null}

        {/* x축 라벨 */}
        {series.map((point, index) =>
          index % labelEvery === 0 ? (
            <text
              key={`label-${point.bucketStart}`}
              x={x(index) + slot / 2}
              y={HEIGHT - 8}
              textAnchor="middle"
              className="ts-axis-text"
            >
              {timeFormat.format(new Date(point.bucketStart))}
            </text>
          ) : null
        )}

        {/* hover/focus 히트 영역: 스크린리더용 값 낭독 포함 */}
        {series.map((point, index) => (
          <rect
            key={`hit-${point.bucketStart}`}
            x={x(index)}
            y={PAD_TOP}
            width={slot}
            height={baseline - PAD_TOP}
            fill="transparent"
            tabIndex={0}
            role="img"
            aria-label={bucketAria(point)}
            onMouseEnter={() => setActiveIndex(index)}
            onFocus={() => setActiveIndex(index)}
            onBlur={() => setActiveIndex(null)}
            onClick={() => setActiveIndex(index)}
          />
        ))}
      </svg>

      {active && activeIndex != null ? (
        <div
          className="ts-tooltip"
          style={{
            left: `${Math.min(Math.max(((activeIndex + 0.5) / series.length) * 100, 12), 88)}%`
          }}
          role="status"
        >
          <strong>{tooltipTimeFormat.format(new Date(active.bucketStart))}</strong>
          <span>수집 {formatCompact(active.collectedTransactions)}건</span>
          <span>탐지 {formatCompact(active.detectedEvents)}건</span>
          <span>탐지율 {formatPercent(active.detectionRatePercent)}</span>
        </div>
      ) : null}

      <div className="ts-legend" aria-hidden="true">
        <span className="ts-legend-item">
          <i className="ts-swatch bar" /> 수집 트랜잭션
        </span>
        <span className="ts-legend-item">
          <i className="ts-swatch line-detected" /> 탐지 이벤트
        </span>
        <span className="ts-legend-item">
          <i className="ts-swatch line-rate" /> 탐지율(우측 축)
        </span>
      </div>
    </div>
  );
}
