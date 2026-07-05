import type { ChartDatum } from "../lib/events";

interface DistributionChartProps {
  data: ChartDatum[];
  emptyMessage: string;
}

export function DistributionChart({ data, emptyMessage }: DistributionChartProps) {
  if (data.length === 0) {
    return <div className="empty-state">{emptyMessage}</div>;
  }

  const max = Math.max(...data.map((datum) => datum.count));

  return (
    <div className="bar-chart" role="img" aria-label="이벤트 유형 분포 차트">
      {data.map((datum) => (
        <div className="bar-row" key={datum.key}>
          <span className="bar-label">{datum.label}</span>
          <div className="bar-track">
            <div
              className="bar-fill"
              style={{ width: `${Math.max((datum.count / max) * 100, 4)}%` }}
            />
          </div>
          <strong className="bar-value">{datum.count}</strong>
        </div>
      ))}
    </div>
  );
}
