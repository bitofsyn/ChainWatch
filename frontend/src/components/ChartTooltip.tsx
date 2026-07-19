import type { OpsSeriesPoint } from "../types";
import type { SeriesKey } from "./ChartLegend";
import { clampTooltipPercent } from "../lib/chartGeometry";
import { formatCompact, formatPercent } from "../lib/opsOverview";

interface ChartTooltipProps {
  point: OpsSeriesPoint;
  index: number;
  seriesLength: number;
  hidden: ReadonlySet<SeriesKey>;
  /** 아직 집계가 끝나지 않은 진행 중 버킷 */
  partial: boolean;
  /** 클릭/키보드로 고정 선택된 상태 */
  pinned: boolean;
}

const tooltipTimeFormat = new Intl.DateTimeFormat("ko-KR", {
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit"
});

/**
 * 모든 series 값을 담은 unified tooltip.
 * 좌우 가장자리에서 잘리지 않게 위치를 클램프하고, 숫자는 tabular-nums로 정렬한다.
 * 시리즈 순서는 범례와 동일하다(수집 → 탐지 → 탐지율).
 */
export function ChartTooltip({ point, index, seriesLength, hidden, partial, pinned }: ChartTooltipProps) {
  return (
    <div
      className={`ts-tooltip ${pinned ? "pinned" : ""}`}
      style={{ left: `${clampTooltipPercent(index, seriesLength)}%` }}
    >
      <strong>
        {tooltipTimeFormat.format(new Date(point.bucketStart))}
        {partial ? <em className="ts-partial-badge">집계 중</em> : null}
      </strong>
      {!hidden.has("collected") ? (
        <span>
          수집 <b>{formatCompact(Math.round(point.collectedTransactions))}건</b>
        </span>
      ) : null}
      {!hidden.has("detected") ? (
        <span>
          탐지 <b>{formatCompact(Math.round(point.detectedEvents))}건</b>
        </span>
      ) : null}
      {!hidden.has("rate") ? (
        <span>
          탐지율 <b>{formatPercent(point.detectionRatePercent)}</b>
        </span>
      ) : null}
      {pinned ? <small className="ts-tooltip-hint">Esc로 해제</small> : null}
    </div>
  );
}
