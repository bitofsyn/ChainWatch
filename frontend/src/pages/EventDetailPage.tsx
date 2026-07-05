import { startTransition, useEffect, useState } from "react";
import {
  ApiError,
  fetchEventDetail,
  fetchTransaction,
  requestAnalysis,
  updateEventStatus
} from "../api";
import type { DetectionEventDetail, EventLifecycleStatus, TransactionItem } from "../types";
import {
  formatDate,
  formatEventType,
  formatLifecycleStatus,
  LIFECYCLE_STATUS_ORDER
} from "../lib/format";
import { isAdmin } from "../lib/auth";
import { RiskBadge } from "../components/RiskBadge";
import { StatusBadge } from "../components/StatusBadge";

interface EventDetailPageProps {
  eventId: number;
}

const ANALYSIS_STATUS_LABELS: Record<string, string> = {
  PENDING: "분석 대기",
  IN_PROGRESS: "분석 중",
  COMPLETED: "분석 완료",
  FAILED: "분석 실패"
};

export function EventDetailPage({ eventId }: EventDetailPageProps) {
  const [event, setEvent] = useState<DetectionEventDetail | null>(null);
  const [transaction, setTransaction] = useState<TransactionItem | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [analyzing, setAnalyzing] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [statusSaving, setStatusSaving] = useState(false);

  useEffect(() => {
    let active = true;

    async function load() {
      setLoading(true);
      setEvent(null);
      setTransaction(null);
      try {
        const detail = await fetchEventDetail(eventId);
        let tx: TransactionItem | null = null;
        if (detail.transactionId != null) {
          try {
            tx = await fetchTransaction(detail.transactionId);
          } catch {
            tx = null;
          }
        }

        if (!active) {
          return;
        }
        startTransition(() => {
          setEvent(detail);
          setTransaction(tx);
          setError(null);
          setLoading(false);
        });
      } catch (cause) {
        if (!active) {
          return;
        }
        const notFound = cause instanceof ApiError && cause.status === 404;
        startTransition(() => {
          setError(
            notFound
              ? `이벤트 #${eventId}를 찾을 수 없습니다.`
              : "이벤트 상세 조회에 실패했습니다. 백엔드 상태를 확인해주세요."
          );
          setLoading(false);
        });
      }
    }

    load();
    return () => {
      active = false;
    };
  }, [eventId]);

  const handleAnalyze = async () => {
    setAnalyzing(true);
    setActionError(null);
    try {
      const report = await requestAnalysis(eventId);
      setEvent((current) => (current ? { ...current, aiReport: report } : current));
    } catch (cause) {
      setActionError(resolveActionError(cause, "AI 분석 요청에 실패했습니다. 분석 서버 상태를 확인해주세요."));
    } finally {
      setAnalyzing(false);
    }
  };

  const handleStatusChange = async (status: EventLifecycleStatus) => {
    setStatusSaving(true);
    setActionError(null);
    try {
      const updated = await updateEventStatus(eventId, status);
      setEvent((current) => (current ? { ...current, status: updated.status } : current));
    } catch (cause) {
      setActionError(resolveActionError(cause, "상태 변경에 실패했습니다."));
    } finally {
      setStatusSaving(false);
    }
  };

  return (
    <>
      <section className="page-head">
        <div>
          <a className="back-link" href="#/events">
            ← 이상거래 목록
          </a>
          <p className="eyebrow">이상거래 상세</p>
          <h1>{event ? formatEventType(event.eventType) : `이벤트 #${eventId}`}</h1>
        </div>
        {error ? <div className="banner error">{error}</div> : null}
      </section>

      {loading ? <div className="empty-state">불러오는 중...</div> : null}

      {event ? (
        <section className="detail-grid">
          <article className="glass-card">
            <div className="section-head compact">
              <div>
                <p className="section-kicker">탐지 요약</p>
                <h2>이벤트 #{event.id}</h2>
              </div>
              <div className="badge-group">
                <RiskBadge riskLevel={event.riskLevel} riskScore={event.riskScore} />
                <StatusBadge status={event.status} />
              </div>
            </div>

            <div className="risk-score-panel">
              <strong>{event.riskScore}</strong>
              <span>위험 점수 (0-100)</span>
              <div className="bar-track">
                <div className="bar-fill" style={{ width: `${event.riskScore}%` }} />
              </div>
            </div>

            <dl className="detail-list">
              <div>
                <dt>탐지 근거</dt>
                <dd>{event.summary}</dd>
              </div>
              <div>
                <dt>탐지 시각</dt>
                <dd>{formatDate(event.detectedAt)}</dd>
              </div>
              <div>
                <dt>탐지 규칙</dt>
                <dd>
                  <a href="#/rules">{formatEventType(event.eventType)} 규칙 기준 보기</a>
                </dd>
              </div>
            </dl>
          </article>

          <article className="glass-card">
            <div className="section-head compact">
              <div>
                <p className="section-kicker">온체인 근거</p>
                <h2>원본 트랜잭션</h2>
              </div>
              {event.transactionId != null ? (
                <a className="ghost-button" href={`#/transactions/${event.transactionId}`}>
                  트랜잭션 상세
                </a>
              ) : null}
            </div>

            {transaction ? (
              <dl className="detail-list">
                <div>
                  <dt>해시</dt>
                  <dd className="mono">{transaction.txHash}</dd>
                </div>
                <div>
                  <dt>금액</dt>
                  <dd>{transaction.amount} ETH</dd>
                </div>
                <div>
                  <dt>블록 번호</dt>
                  <dd>{transaction.blockNumber}</dd>
                </div>
                <div>
                  <dt>체결 시각</dt>
                  <dd>{formatDate(transaction.timestamp)}</dd>
                </div>
              </dl>
            ) : (
              <div className="empty-state">연결된 트랜잭션 정보가 없습니다.</div>
            )}

            <div className="section-head compact related-head">
              <div>
                <p className="section-kicker">연관 지갑</p>
                <h2>관련 주소</h2>
              </div>
            </div>
            <dl className="detail-list">
              <div>
                <dt>탐지 지갑</dt>
                <dd className="mono">
                  <a href={`#/wallets/${encodeURIComponent(event.walletAddress)}`}>
                    {event.walletAddress}
                  </a>
                </dd>
              </div>
              {transaction ? (
                <>
                  <div>
                    <dt>보낸 주소</dt>
                    <dd className="mono">
                      <a href={`#/wallets/${encodeURIComponent(transaction.fromAddress)}`}>
                        {transaction.fromAddress}
                      </a>
                    </dd>
                  </div>
                  <div>
                    <dt>받는 주소</dt>
                    <dd className="mono">
                      {transaction.toAddress ? (
                        <a href={`#/wallets/${encodeURIComponent(transaction.toAddress)}`}>
                          {transaction.toAddress}
                        </a>
                      ) : (
                        "-"
                      )}
                    </dd>
                  </div>
                </>
              ) : null}
            </dl>
          </article>

          <article className="glass-card wide-span">
            <div className="section-head compact">
              <div>
                <p className="section-kicker">AI 해설</p>
                <h2>탐지 근거 분석 리포트</h2>
              </div>
              {isAdmin() ? (
                <button
                  type="button"
                  className="ghost-button"
                  onClick={handleAnalyze}
                  disabled={analyzing}
                >
                  {analyzing ? "분석 요청 중..." : event.aiReport ? "재분석 요청" : "AI 분석 요청"}
                </button>
              ) : null}
            </div>

            {event.aiReport ? (
              <>
                <div className="report-meta">
                  <span className="status-pill elevated">
                    {ANALYSIS_STATUS_LABELS[event.aiReport.status] ?? event.aiReport.status}
                  </span>
                  {event.aiReport.provider ? (
                    <span>
                      {event.aiReport.provider}
                      {event.aiReport.model ? ` · ${event.aiReport.model}` : ""}
                    </span>
                  ) : null}
                  {event.aiReport.analyzedAt ? <span>{formatDate(event.aiReport.analyzedAt)}</span> : null}
                </div>
                {event.aiReport.report ? (
                  <pre className="report-body">{event.aiReport.report}</pre>
                ) : (
                  <div className="empty-state">리포트 본문이 아직 생성되지 않았습니다.</div>
                )}
              </>
            ) : (
              <div className="empty-state">
                AI 리포트는 룰 기반 탐지 결과를 설명하는 보조 자료입니다.{" "}
                {isAdmin()
                  ? "상단 버튼으로 분석을 요청하세요."
                  : "관리자가 분석을 요청하면 여기에 표시됩니다."}
              </div>
            )}
          </article>

          <article className="glass-card wide-span">
            <div className="section-head compact">
              <div>
                <p className="section-kicker">운영 액션</p>
                <h2>처리 상태 관리</h2>
              </div>
            </div>

            {actionError ? <div className="banner error">{actionError}</div> : null}

            {isAdmin() ? (
              <div className="status-actions">
                {LIFECYCLE_STATUS_ORDER.map((status) => (
                  <button
                    key={status}
                    type="button"
                    className={`ghost-button ${event.status === status ? "selected" : ""}`}
                    disabled={statusSaving || event.status === status}
                    onClick={() => handleStatusChange(status)}
                  >
                    {formatLifecycleStatus(status)}
                  </button>
                ))}
              </div>
            ) : (
              <div className="empty-state">
                현재 상태: {formatLifecycleStatus(event.status)}. 상태 변경은 관리자 로그인 후
                가능합니다.
              </div>
            )}
          </article>
        </section>
      ) : null}
    </>
  );
}

function resolveActionError(cause: unknown, fallback: string): string {
  if (cause instanceof ApiError && (cause.status === 401 || cause.status === 403)) {
    return "관리자 인증이 만료되었습니다. 관리자 페이지에서 다시 로그인해주세요.";
  }
  if (cause instanceof ApiError && cause.serverMessage) {
    return cause.serverMessage;
  }
  return fallback;
}
