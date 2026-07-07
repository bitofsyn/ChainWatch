import type { TransactionItem } from "../types";
import { resolveConfirmation } from "../lib/ruleEvidence";

/**
 * 트랜잭션 확정 상태 배지 (Wave2 confirmations 계약).
 * 확정 / 미확정(reorg 되감기 가능 구간) / 판정 불가(head 미관측)를 구분한다.
 */
export function ConfirmationBadge({
  transaction
}: {
  transaction: Pick<TransactionItem, "confirmations" | "confirmed">;
}) {
  const view = resolveConfirmation(transaction);
  return (
    <span className="confirmation-cell">
      <span className={`status-pill confirm-${view.kind}`} title={view.detail ?? undefined}>
        {view.label}
      </span>
      {view.detail ? <small className="confirmation-detail">{view.detail}</small> : null}
    </span>
  );
}
