import type { KpiLevel } from "../lib/opsOverview";
import { KPI_LEVEL_LABELS } from "../lib/opsOverview";

interface MetricCardProps {
  label: string;
  /** 표시 값. 데이터가 없으면 호출부에서 "—"를 넘긴다. */
  value: string;
  /** 보조 설명(마지막 수집 블록, 증감률 등) */
  sub?: string;
  /** 정상/주의/위험 판정. 값이 없으면 배지를 표시하지 않는다. */
  level?: KpiLevel;
  /** threshold 기준 설명. title tooltip과 스크린리더에 노출된다. */
  help?: string;
}

/** compact KPI 카드: 상태를 색상+텍스트 배지로 함께 표기한다. */
export function MetricCard({ label, value, sub, level, help }: MetricCardProps) {
  return (
    <article className="ops-kpi" title={help}>
      <div className="ops-kpi-head">
        <span className="ops-kpi-label">{label}</span>
        {level ? (
          <span className={`ops-kpi-level level-${level}`}>{KPI_LEVEL_LABELS[level]}</span>
        ) : null}
      </div>
      <strong className="ops-kpi-value">{value}</strong>
      {sub ? <small className="ops-kpi-sub">{sub}</small> : null}
      {help ? <span className="sr-only">{help}</span> : null}
    </article>
  );
}
