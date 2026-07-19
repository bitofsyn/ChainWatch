import { useEffect, useState, type ReactNode } from "react";
import type { KpiLevel } from "../lib/opsOverview";
import { KPI_LEVEL_LABELS } from "../lib/opsOverview";
import { changeTone, type KpiSemantic, type ValueChange } from "../lib/overviewDiff";
import { usePrefersReducedMotion } from "../hooks/usePrefersReducedMotion";

interface MetricCardProps {
  label: string;
  /** 표시 값. 데이터가 없으면 호출부에서 "—"를 넘긴다. */
  value: ReactNode;
  /** 보조 설명(마지막 수집 블록, 증감률 등) */
  sub?: string;
  /** 정상/주의/위험 판정. 값이 없으면 배지를 표시하지 않는다. */
  level?: KpiLevel;
  /** threshold 기준 설명. title tooltip과 스크린리더에 노출된다. */
  help?: string;
  /** 직전 성공 응답과의 diff. up/down일 때만 1회 flash + delta chip을 표시한다. */
  change?: ValueChange | null;
  /** 상승이 항상 긍정이 아니므로 KPI별 의미 방향 */
  semantic?: KpiSemantic;
  /** delta chip 텍스트 포맷터 (예: "+18건", "+12.4%") */
  formatChipDelta?: (change: ValueChange) => string | null;
}

/** delta chip 최소 유지 시간: 급격히 사라져 읽기 어렵지 않게 한다. */
const CHIP_VISIBLE_MS = 5_000;
const FLASH_MS = 800;

/** compact KPI 카드: 상태를 색상+텍스트 배지로 함께 표기한다. */
export function MetricCard({
  label,
  value,
  sub,
  level,
  help,
  change,
  semantic = "neutral",
  formatChipDelta
}: MetricCardProps) {
  const reducedMotion = usePrefersReducedMotion();
  const [chipVisible, setChipVisible] = useState(false);
  const [flashing, setFlashing] = useState(false);

  const isRealChange = change != null && (change.direction === "up" || change.direction === "down");
  const tone = change != null ? changeTone(change, semantic) : "neutral";
  const changedAt = isRealChange ? change.changedAt : null;

  // 실제 데이터 변경 시에만 chip 노출 + 1회 flash. changedAt이 트리거 키다.
  useEffect(() => {
    if (changedAt == null) {
      return;
    }
    setChipVisible(true);
    const chipTimer = setTimeout(() => setChipVisible(false), CHIP_VISIBLE_MS);
    let flashTimer: ReturnType<typeof setTimeout> | null = null;
    if (!reducedMotion) {
      setFlashing(true);
      flashTimer = setTimeout(() => setFlashing(false), FLASH_MS);
    }
    return () => {
      clearTimeout(chipTimer);
      if (flashTimer != null) {
        clearTimeout(flashTimer);
      }
    };
  }, [changedAt, reducedMotion]);

  const chipText = isRealChange && formatChipDelta ? formatChipDelta(change) : null;

  return (
    <article
      className={`ops-kpi ${flashing ? `kpi-flash tone-${tone}` : ""}`}
      title={help}
    >
      <div className="ops-kpi-head">
        <span className="ops-kpi-label">{label}</span>
        {level ? (
          <span className={`ops-kpi-level level-${level}`}>{KPI_LEVEL_LABELS[level]}</span>
        ) : null}
      </div>
      <div className="ops-kpi-value-row">
        <strong className="ops-kpi-value">{value}</strong>
        {chipVisible && chipText ? (
          <span className={`kpi-delta-chip tone-${tone}`}>{chipText}</span>
        ) : null}
      </div>
      {sub ? <small className="ops-kpi-sub">{sub}</small> : null}
      {help ? <span className="sr-only">{help}</span> : null}
    </article>
  );
}
