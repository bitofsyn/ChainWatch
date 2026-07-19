export type SeriesKey = "collected" | "detected" | "rate";

export const SERIES_ORDER: SeriesKey[] = ["collected", "detected", "rate"];

export const SERIES_LABELS: Record<SeriesKey, string> = {
  collected: "수집 트랜잭션",
  detected: "탐지 이벤트",
  rate: "탐지율(우측 축)"
};

interface ChartLegendProps {
  hidden: ReadonlySet<SeriesKey>;
  onToggle: (key: SeriesKey) => void;
}

/**
 * series 표시/숨김 토글 범례. 상태는 aria-pressed로 노출하고
 * 마지막 남은 series는 숨길 수 없다(호출부에서 no-op 처리).
 * 색만이 아니라 swatch 모양(막대/실선/점선)으로도 구분한다.
 */
export function ChartLegend({ hidden, onToggle }: ChartLegendProps) {
  return (
    <div className="ts-legend" role="group" aria-label="시리즈 표시 토글">
      {SERIES_ORDER.map((key) => {
        const visible = !hidden.has(key);
        return (
          <button
            key={key}
            type="button"
            className={`ts-legend-item ${visible ? "" : "muted"}`}
            aria-pressed={visible}
            onClick={() => onToggle(key)}
          >
            <i
              className={`ts-swatch ${
                key === "collected" ? "bar" : key === "detected" ? "line-detected" : "line-rate"
              }`}
              aria-hidden="true"
            />
            {SERIES_LABELS[key]}
          </button>
        );
      })}
    </div>
  );
}
