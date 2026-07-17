import type { OpsRiskStatusCell } from "../types";
import {
  LIFECYCLE_STATUS_LABELS,
  LIFECYCLE_STATUS_ORDER,
  RISK_LEVEL_LABELS
} from "../lib/format";
import { formatNumber } from "../lib/opsOverview";

interface RiskStatusMatrixProps {
  cells: OpsRiskStatusCell[];
}

const RISK_ORDER = ["CRITICAL", "HIGH", "MEDIUM", "LOW"];

/**
 * 위험도×처리상태 매트릭스. 셀은 항상 숫자를 표기하고(색만으로 전달 금지),
 * 건수가 있는 셀은 해당 필터가 적용된 이벤트 목록으로 딥링크한다.
 */
export function RiskStatusMatrix({ cells }: RiskStatusMatrixProps) {
  const countOf = (risk: string, status: string) =>
    cells.find((cell) => cell.riskLevel === risk && cell.status === status)?.count ?? 0;
  const max = Math.max(1, ...cells.map((cell) => cell.count));
  const rowTotal = (risk: string) =>
    cells.filter((cell) => cell.riskLevel === risk).reduce((sum, cell) => sum + cell.count, 0);

  return (
    <div className="table-scroll">
      <table className="risk-matrix">
        <caption className="sr-only">위험도와 처리 상태별 탐지 이벤트 수. 셀을 선택하면 해당 필터의 이벤트 목록으로 이동합니다.</caption>
        <thead>
          <tr>
            <th scope="col">위험도</th>
            {LIFECYCLE_STATUS_ORDER.map((status) => (
              <th key={status} scope="col">
                {LIFECYCLE_STATUS_LABELS[status]}
              </th>
            ))}
            <th scope="col">합계</th>
          </tr>
        </thead>
        <tbody>
          {RISK_ORDER.map((risk) => (
            <tr key={risk}>
              <th scope="row" className={`risk-row-head risk-${risk.toLowerCase()}`}>
                {RISK_LEVEL_LABELS[risk] ?? risk}
              </th>
              {LIFECYCLE_STATUS_ORDER.map((status) => {
                const count = countOf(risk, status);
                // 3단계 농도: 0 / 낮음 / 높음(최대 대비 40% 이상)
                const intensity = count === 0 ? 0 : count >= max * 0.4 ? 2 : 1;
                return (
                  <td key={status} className={`matrix-cell intensity-${intensity}`}>
                    {count > 0 ? (
                      <a
                        href={`#/events?riskLevel=${risk}&status=${status}`}
                        aria-label={`${RISK_LEVEL_LABELS[risk] ?? risk} × ${LIFECYCLE_STATUS_LABELS[status]} ${count}건 보기`}
                      >
                        {formatNumber(count)}
                      </a>
                    ) : (
                      <span className="matrix-zero">0</span>
                    )}
                  </td>
                );
              })}
              <td className="matrix-total">{formatNumber(rowTotal(risk))}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
