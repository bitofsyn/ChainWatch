import { useCallback, useEffect, useState } from "react";
import { ApiError, decidePolicySuggestion, fetchPolicySuggestions } from "../api";
import { useAuth } from "../contexts/AuthContext";
import { formatDate, shortenAddress } from "../lib/format";
import type { PolicySuggestion, PolicySuggestionStatus } from "../types";

const STATUS_LABELS: Record<PolicySuggestionStatus, string> = {
  PENDING: "검토 대기",
  ACCEPTED: "예외 적용 중",
  REJECTED: "반려",
  REVOKED: "예외 해제"
};

/**
 * 분석가가 올린 탐지 예외 주소 제안 목록. 관리자는 승인·반려하고 승인된 예외를 해제할 수 있다.
 * 승인된 주소는 탐지 엔진이 이벤트를 만들지 않는다(제재 주소 탐지는 예외 불가).
 */
export function PolicySuggestionsPanel() {
  const { isAdmin } = useAuth();
  const [items, setItems] = useState<PolicySuggestion[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<number | null>(null);

  const load = useCallback(async () => {
    try {
      setItems(await fetchPolicySuggestions());
      setError(null);
    } catch (cause) {
      setError(cause instanceof ApiError && cause.message ? cause.message : "제안 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function decide(id: number, decision: "ACCEPT" | "REJECT" | "REVOKE") {
    setBusy(id);
    try {
      await decidePolicySuggestion(id, decision);
      await load();
    } catch (cause) {
      setError(cause instanceof ApiError && cause.message ? cause.message : "결정을 저장하지 못했습니다.");
    } finally {
      setBusy(null);
    }
  }

  const pending = items.filter((item) => item.status === "PENDING").length;

  return (
    <section className="panel-card">
      <div className="section-head compact">
        <div>
          <p className="section-kicker">분석가 제안</p>
          <h2>
            탐지 예외 주소 <span className="cell-muted">검토 대기 {pending}건</span>
          </h2>
        </div>
      </div>
      <p className="cell-muted">
        분석가가 오탐으로 판단한 주소를 예외로 제안하면 여기에 쌓입니다. 승인하면 그 주소는 이후 탐지에서 제외되고,
        언제든 해제할 수 있습니다. 제재 주소 탐지는 예외로도 끌 수 없습니다.
      </p>
      {error ? (
        <div className="banner error" role="alert">
          {error}
        </div>
      ) : null}
      {loading ? (
        <p className="cell-muted">불러오는 중…</p>
      ) : items.length === 0 ? (
        <p className="cell-muted">아직 제안이 없습니다. 분석가가 이벤트 상세에서 "예외 주소 제안"을 누르면 등록됩니다.</p>
      ) : (
        <div className="table-scroll">
          <table className="data-table" aria-label="탐지 예외 주소 제안">
            <thead>
              <tr>
                <th scope="col">주소</th>
                <th scope="col">사유</th>
                <th scope="col">제안자</th>
                <th scope="col">상태</th>
                <th scope="col">제안 시각</th>
                {isAdmin ? <th scope="col">결정</th> : null}
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.id}>
                  <td className="mono" title={item.address}>
                    <a className="row-link" href={`#/wallets/${item.address}`}>
                      {shortenAddress(item.address)}
                    </a>
                  </td>
                  <td className="cell-summary" title={item.reason}>
                    {item.eventId ? (
                      <a className="row-link" href={`#/events/${item.eventId}`}>
                        #{item.eventId}
                      </a>
                    ) : null}{" "}
                    {item.reason}
                  </td>
                  <td>{item.proposedBy}</td>
                  <td>
                    <span className={`suggestion-status status-${item.status.toLowerCase()}`}>
                      {STATUS_LABELS[item.status] ?? item.status}
                    </span>
                    {item.decidedBy ? <small className="cell-muted"> · {item.decidedBy}</small> : null}
                  </td>
                  <td className="cell-time">{formatDate(item.createdAt)}</td>
                  {isAdmin ? (
                    <td>
                      {item.status === "PENDING" ? (
                        <span className="inline-actions">
                          <button
                            type="button"
                            className="ghost-button"
                            onClick={() => decide(item.id, "ACCEPT")}
                            disabled={busy != null}
                          >
                            승인
                          </button>
                          <button
                            type="button"
                            className="ghost-button"
                            onClick={() => decide(item.id, "REJECT")}
                            disabled={busy != null}
                          >
                            반려
                          </button>
                        </span>
                      ) : item.status === "ACCEPTED" ? (
                        <button
                          type="button"
                          className="ghost-button"
                          onClick={() => decide(item.id, "REVOKE")}
                          disabled={busy != null}
                        >
                          예외 해제
                        </button>
                      ) : (
                        <span className="cell-muted">—</span>
                      )}
                    </td>
                  ) : null}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
