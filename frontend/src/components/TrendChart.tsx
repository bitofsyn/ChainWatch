import type { EventTrend } from "../lib/events";

interface TrendChartProps {
  trend: EventTrend | null;
  emptyMessage: string;
}

function hourLabel(iso: string): string {
  const date = new Date(iso);
  return `${String(date.getHours()).padStart(2, "0")}시`;
}

function fullLabel(iso: string, count: number): string {
  const date = new Date(iso);
  return `${date.getMonth() + 1}/${date.getDate()} ${String(date.getHours()).padStart(2, "0")}:00 — ${count}건`;
}

/**
 * 시간대별 탐지 추이 세로 바 차트.
 * styles.css를 수정하지 않기 위해 인라인 스타일 + 기존 CSS 변수만 사용한다.
 */
export function TrendChart({ trend, emptyMessage }: TrendChartProps) {
  const points = trend?.points ?? [];
  if (points.length === 0 || points.every((point) => point.count === 0)) {
    return <div className="empty-state">{emptyMessage}</div>;
  }

  const max = Math.max(...points.map((point) => point.count), 1);
  const labelEvery = Math.max(1, Math.floor(points.length / 6));

  return (
    <div role="img" aria-label={`최근 ${trend?.hours ?? points.length}시간 탐지 추이 차트`}>
      <div
        style={{
          display: "flex",
          alignItems: "flex-end",
          gap: "3px",
          height: "140px",
          padding: "4px 0",
          borderBottom: "1px solid var(--surface-line)"
        }}
      >
        {points.map((point) => (
          <div
            key={point.bucketStart}
            title={fullLabel(point.bucketStart, point.count)}
            style={{
              flex: 1,
              height: `${Math.max((point.count / max) * 100, point.count > 0 ? 4 : 1)}%`,
              background: point.count > 0 ? "var(--accent)" : "var(--surface-line)",
              borderRadius: "2px 2px 0 0",
              minWidth: "3px"
            }}
          />
        ))}
      </div>
      <div
        style={{
          display: "flex",
          gap: "3px",
          marginTop: "6px",
          color: "var(--text-soft)",
          fontSize: "0.72rem"
        }}
      >
        {points.map((point, index) => (
          <span key={point.bucketStart} style={{ flex: 1, textAlign: "center" }}>
            {index % labelEvery === 0 ? hourLabel(point.bucketStart) : ""}
          </span>
        ))}
      </div>
    </div>
  );
}
