import { startTransition, useEffect, useState } from "react";
import type { FormEvent } from "react";
import {
  ApiError,
  fetchEventDetail,
  fetchTransaction,
  requestAnalysis,
  updateEventStatus
} from "../api";
import type {
  AiStructuredAnalysis,
  DetectionEventDetail,
  EventLifecycleStatus,
  TransactionItem
} from "../types";
import {
  formatAmount,
  formatDate,
  formatEventType,
  formatFullDate,
  formatLifecycleStatus,
  LIFECYCLE_STATUS_ORDER,
  shortenAddress
} from "../lib/format";
import { buildStatusPatchBody, requiredReasonField, validateStatusChange } from "../lib/workflow";
import { formatConfidence, formatEscalation, resolveAiReportView } from "../lib/aiReport";
import { resolveRuleEvidence } from "../lib/ruleEvidence";
import { ConfirmationBadge } from "../components/ConfirmationBadge";
import { useAuth } from "../contexts/AuthContext";
import { RiskBadge } from "../components/RiskBadge";
import { StatusBadge } from "../components/StatusBadge";
import { ChainBadge } from "../components/ChainBadge";
import { DataState } from "../components/DataState";

interface EventDetailPageProps {
  eventId: number;
}

export function EventDetailPage({ eventId }: EventDetailPageProps) {
  const [event, setEvent] = useState<DetectionEventDetail | null>(null);
  const [transaction, setTransaction] = useState<TransactionItem | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

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
  }, [eventId, reloadKey]);

  return (
    <>
      <section className="page-head">
        <div>
          <a className="back-link" href="#/events">
            ← 이상거래 큐
          </a>
          <p className="eyebrow">이상거래 상세 · 이벤트 #{eventId}</p>
          <h1>{event ? formatEventType(event.eventType) : `이벤트 #${eventId}`}</h1>
        </div>
      </section>

      <DataState
        loading={loading}
        error={error}
        onRetry={() => setReloadKey((key) => key + 1)}
        loadingMessage="이벤트 상세를 불러오는 중..."
      />

      {event ? (
        <>
          <SummaryStrip event={event} />

          <section className="detail-grid">
            <RuleEvidenceSection event={event} />
            <TransactionEvidenceSection event={event} transaction={transaction} />
            <AiAnalysisSection event={event} onReportChange={setEvent} />
            <OperatorActionSection event={event} onEventChange={setEvent} />
          </section>
        </>
      ) : null}
    </>
  );
}

/* ── 상단 요약 스트립 ─────────────────────────── */

function SummaryStrip({ event }: { event: DetectionEventDetail }) {
  return (
    <section className="summary-strip" aria-label="이벤트 요약">
      <div className="summary-cell">
        <span>위험</span>
        <div className="summary-value">
          <strong className="summary-score">{event.riskScore}</strong>
          <RiskBadge riskLevel={event.riskLevel} riskScore={event.riskScore} />
        </div>
      </div>
      <div className="summary-cell">
        <span>상태</span>
        <div className="summary-value">
          <StatusBadge status={event.status} />
        </div>
        {event.statusChangedAt ? <small>{formatDate(event.statusChangedAt)} 변경</small> : null}
      </div>
      <div className="summary-cell">
        <span>유형</span>
        <div className="summary-value">{formatEventType(event.eventType)}</div>
      </div>
      <div className="summary-cell">
        <span>체인</span>
        <div className="summary-value">
          <ChainBadge network={event.network} />
        </div>
      </div>
      <div className="summary-cell">
        <span>담당자</span>
        <div className="summary-value">
          {event.assignee || <span className="cell-muted">미지정</span>}
        </div>
      </div>
      <div className="summary-cell">
        <span>지갑</span>
        <div className="summary-value mono">
          <a href={`#/wallets/${encodeURIComponent(event.walletAddress)}`} title={event.walletAddress}>
            {shortenAddress(event.walletAddress)}
          </a>
        </div>
      </div>
      <div className="summary-cell">
        <span>트랜잭션</span>
        <div className="summary-value mono">
          {event.txHash ? (
            event.transactionId != null ? (
              <a href={`#/transactions/${event.transactionId}`} title={event.txHash}>
                {shortenAddress(event.txHash)}
              </a>
            ) : (
              <span title={event.txHash}>{shortenAddress(event.txHash)}</span>
            )
          ) : (
            "-"
          )}
        </div>
      </div>
      <div className="summary-cell">
        <span>탐지 시각</span>
        <div className="summary-value">{formatDate(event.detectedAt)}</div>
      </div>
    </section>
  );
}

/* ── 룰 근거 ─────────────────────────────────── */

function RuleEvidenceSection({ event }: { event: DetectionEventDetail }) {
  const evidenceEntries = resolveRuleEvidence(event.evidence);

  return (
    <article className="panel-card">
      <div className="section-head compact">
        <div>
          <p className="section-kicker">룰 근거</p>
          <h2>탐지 규칙 판정</h2>
        </div>
        <a className="ghost-button" href="#/rules">
          규칙 기준 보기
        </a>
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
          <dt>탐지 규칙</dt>
          <dd>
            {formatEventType(event.eventType)}
            {event.ruleVersion ? (
              <span className="rule-version mono"> v{event.ruleVersion}</span>
            ) : null}
          </dd>
        </div>
        <div>
          <dt>탐지 근거</dt>
          <dd>{event.summary}</dd>
        </div>
        <div>
          <dt>탐지 시각</dt>
          <dd>{formatFullDate(event.detectedAt)}</dd>
        </div>
      </dl>

      {evidenceEntries ? (
        <>
          <div className="section-head compact related-head">
            <div>
              <p className="section-kicker">발화 근거 데이터</p>
              <h2>룰 evidence</h2>
            </div>
          </div>
          <dl className="detail-list evidence-list">
            {evidenceEntries.map((entry) => (
              <div key={entry.key}>
                <dt
                  className={entry.known ? undefined : "mono"}
                  title={entry.known ? entry.key : undefined}
                >
                  {entry.label}
                </dt>
                <dd className={entry.mono ? "mono" : undefined}>{entry.value}</dd>
              </div>
            ))}
          </dl>
        </>
      ) : (
        <div className="evidence-empty">
          <DataState
            empty
            emptyMessage="구조화된 룰 근거 데이터가 없습니다 (레거시 이벤트이거나 evidence 미기록)."
          />
        </div>
      )}
    </article>
  );
}

/* ── 트랜잭션 근거 ────────────────────────────── */

function TransactionEvidenceSection({
  event,
  transaction
}: {
  event: DetectionEventDetail;
  transaction: TransactionItem | null;
}) {
  return (
    <article className="panel-card">
      <div className="section-head compact">
        <div>
          <p className="section-kicker">온체인 근거</p>
          <h2>트랜잭션 증거</h2>
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
            <dt>확정 여부</dt>
            <dd>
              <ConfirmationBadge transaction={transaction} />
            </dd>
          </div>
          <div>
            <dt>금액</dt>
            <dd className="strong-num">{formatAmount(transaction.amount)}</dd>
          </div>
          <div>
            <dt>블록 번호</dt>
            <dd>{transaction.blockNumber}</dd>
          </div>
          <div>
            <dt>체결 시각</dt>
            <dd>{formatFullDate(transaction.timestamp)}</dd>
          </div>
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
        </dl>
      ) : (
        <DataState empty emptyMessage="연결된 트랜잭션 정보가 없습니다." />
      )}

      <dl className="detail-list related-list">
        <div>
          <dt>탐지 지갑</dt>
          <dd className="mono">
            <a href={`#/wallets/${encodeURIComponent(event.walletAddress)}`}>{event.walletAddress}</a>
          </dd>
        </div>
      </dl>
    </article>
  );
}

/* ── AI 보조 분석 ─────────────────────────────── */

function AiAnalysisSection({
  event,
  onReportChange
}: {
  event: DetectionEventDetail;
  onReportChange: (updater: (current: DetectionEventDetail | null) => DetectionEventDetail | null) => void;
}) {
  const [analyzing, setAnalyzing] = useState(false);
  const [analysisError, setAnalysisError] = useState<string | null>(null);
  const view = resolveAiReportView(event.aiReport);
  const authed = useAuth().user != null;

  const handleAnalyze = async () => {
    setAnalyzing(true);
    setAnalysisError(null);
    try {
      const report = await requestAnalysis(event.id);
      onReportChange((current) => (current ? { ...current, aiReport: report } : current));
    } catch (cause) {
      setAnalysisError(
        resolveActionError(cause, "AI 분석 요청에 실패했습니다. 분석 서버 상태를 확인해주세요.")
      );
    } finally {
      setAnalyzing(false);
    }
  };

  return (
    <article className="panel-card wide-span">
      <div className="section-head compact">
        <div>
          <p className="section-kicker">AI 보조 분석</p>
          <h2>근거 기반 분석 리포트</h2>
        </div>
        {authed && view.kind !== "failed" ? (
          <button type="button" className="ghost-button" onClick={handleAnalyze} disabled={analyzing}>
            {analyzing ? "분석 요청 중..." : view.kind === "none" ? "AI 분석 요청" : "재분석 요청"}
          </button>
        ) : null}
      </div>

      <p className="assist-note">
        AI 리포트는 룰 탐지 결과를 설명하는 <strong>보조 근거</strong>이며 운영 판단을 대체하지
        않습니다. 최종 판정과 상태 변경은 담당 분석가의 책임입니다.
      </p>

      {analysisError ? <div className="banner error">{analysisError}</div> : null}

      {event.aiReport ? (
        <div className="report-meta">
          <span className={`status-pill analysis-${event.aiReport.status.toLowerCase()}`}>
            {ANALYSIS_STATUS_LABELS[event.aiReport.status] ?? event.aiReport.status}
          </span>
          {event.aiReport.provider ? (
            <span>
              {event.aiReport.provider}
              {event.aiReport.model ? ` · ${event.aiReport.model}` : ""}
            </span>
          ) : null}
          {event.aiReport.analyzedAt ? <span>{formatFullDate(event.aiReport.analyzedAt)}</span> : null}
        </div>
      ) : null}

      {view.kind === "none" ? (
        <DataState
          empty
          emptyMessage={
            authed
              ? "아직 분석 리포트가 없습니다. 상단 버튼으로 분석을 요청하세요."
              : "아직 분석 리포트가 없습니다. 분석 요청은 로그인 후 가능합니다."
          }
        />
      ) : null}

      {view.kind === "pending" ? (
        <DataState loading loadingMessage="AI 분석이 진행 중입니다. 잠시 후 다시 확인해주세요." />
      ) : null}

      {view.kind === "failed" ? (
        <div className="data-state error" role="alert">
          <p>AI 분석에 실패했습니다. 분석 서버 또는 프로바이더 상태를 확인한 뒤 재시도할 수 있습니다.</p>
          {authed ? (
            <button type="button" className="ghost-button" onClick={handleAnalyze} disabled={analyzing}>
              {analyzing ? "재시도 중..." : "분석 재시도"}
            </button>
          ) : null}
        </div>
      ) : null}

      {view.kind === "empty" ? (
        <DataState empty emptyMessage="리포트 본문이 아직 생성되지 않았습니다." />
      ) : null}

      {view.kind === "text" ? (
        <>
          {view.degraded ? (
            <div className="banner warn">
              구조화 분석을 생성하지 못해 텍스트 리포트만 표시합니다 (degraded).
            </div>
          ) : null}
          <pre className="report-body">{view.report}</pre>
        </>
      ) : null}

      {view.kind === "structured" ? <StructuredReport analysis={view.analysis} rawReport={view.report} /> : null}
    </article>
  );
}

const ANALYSIS_STATUS_LABELS: Record<string, string> = {
  PENDING: "분석 대기",
  IN_PROGRESS: "분석 중",
  COMPLETED: "분석 완료",
  FAILED: "분석 실패"
};

function StructuredReport({
  analysis,
  rawReport
}: {
  analysis: AiStructuredAnalysis;
  rawReport: string | null;
}) {
  const [showRaw, setShowRaw] = useState(false);
  const confidence = formatConfidence(analysis.confidence);
  const escalation = formatEscalation(analysis.escalationLevel);

  return (
    <div className="ai-structured">
      <div className="ai-badges">
        {confidence ? (
          <span className={`status-pill confidence-${analysis.confidence}`}>{confidence}</span>
        ) : null}
        {escalation ? (
          <span className={`status-pill escalation-${analysis.escalationLevel}`}>{escalation}</span>
        ) : null}
      </div>

      {analysis.riskSummary ? <p className="ai-risk-summary">{analysis.riskSummary}</p> : null}

      <div className="ai-columns">
        <div className="ai-block">
          <h3>근거 데이터</h3>
          {analysis.evidence.length > 0 ? (
            <ul className="ai-evidence-list">
              {analysis.evidence.map((item, index) => (
                <li key={`${item.source}-${index}`}>
                  <span className="evidence-source mono">{item.source}</span>
                  <span>{item.fact}</span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="ai-empty-note">
              근거 데이터 없음 — 모델이 근거 없이 판단했을 수 있으므로 주의하세요.
            </p>
          )}
        </div>

        <div className="ai-block">
          <h3>가능 시나리오</h3>
          {analysis.possibleScenarios.length > 0 ? (
            <ul className="ai-plain-list">
              {analysis.possibleScenarios.map((item, index) => (
                <li key={index}>{item}</li>
              ))}
            </ul>
          ) : (
            <p className="ai-empty-note">제시된 시나리오가 없습니다.</p>
          )}
        </div>

        <div className="ai-block">
          <h3>권장 조치 (우선순위 순)</h3>
          {analysis.recommendedActions.length > 0 ? (
            <ol className="ai-plain-list">
              {analysis.recommendedActions.map((item, index) => (
                <li key={index}>{item}</li>
              ))}
            </ol>
          ) : (
            <p className="ai-empty-note">권장 조치가 없습니다.</p>
          )}
        </div>

        <div className="ai-block">
          <h3>오탐 가능 요인</h3>
          {analysis.falsePositiveFactors.length > 0 ? (
            <ul className="ai-plain-list">
              {analysis.falsePositiveFactors.map((item, index) => (
                <li key={index}>{item}</li>
              ))}
            </ul>
          ) : (
            <p className="ai-empty-note">식별된 오탐 요인이 없습니다.</p>
          )}
        </div>
      </div>

      {rawReport ? (
        <div className="ai-raw">
          <button type="button" className="ghost-button" onClick={() => setShowRaw((value) => !value)}>
            {showRaw ? "원문 리포트 접기" : "원문 리포트 보기"}
          </button>
          {showRaw ? <pre className="report-body">{rawReport}</pre> : null}
        </div>
      ) : null}
    </div>
  );
}

/* ── 운영 액션 (상태 변경 workflow) ─────────────── */

function OperatorActionSection({
  event,
  onEventChange
}: {
  event: DetectionEventDetail;
  onEventChange: (updater: (current: DetectionEventDetail | null) => DetectionEventDetail | null) => void;
}) {
  const [targetStatus, setTargetStatus] = useState<EventLifecycleStatus>(event.status);
  const [assignee, setAssignee] = useState(event.assignee ?? "");
  const [resolutionReason, setResolutionReason] = useState(event.resolutionReason ?? "");
  const [falsePositiveReason, setFalsePositiveReason] = useState(event.falsePositiveReason ?? "");
  const [notes, setNotes] = useState(event.notes ?? "");
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const currentUser = useAuth().user;
  const authed = currentUser != null;
  const reasonField = requiredReasonField(targetStatus);

  const handleSubmit = async (formEvent: FormEvent) => {
    formEvent.preventDefault();
    setSuccess(null);

    const draft = {
      status: targetStatus,
      assignee,
      resolutionReason,
      falsePositiveReason,
      notes
    };
    const validationError = validateStatusChange(draft);
    if (validationError) {
      setFormError(validationError);
      return;
    }

    setSaving(true);
    setFormError(null);
    try {
      const updated = await updateEventStatus(event.id, buildStatusPatchBody(draft));
      onEventChange((current) =>
        current
          ? {
              ...current,
              status: updated.status,
              assignee: updated.assignee ?? null,
              statusChangedAt: updated.statusChangedAt ?? null,
              resolutionReason: updated.resolutionReason ?? null,
              falsePositiveReason: updated.falsePositiveReason ?? null,
              notes: updated.notes ?? null
            }
          : current
      );
      setSuccess(`이벤트 #${event.id} 상태를 '${formatLifecycleStatus(updated.status)}'(으)로 변경했습니다.`);
    } catch (cause) {
      setFormError(resolveActionError(cause, "상태 변경에 실패했습니다. 다시 시도해주세요."));
    } finally {
      setSaving(false);
    }
  };

  if (!authed) {
    return (
      <article className="panel-card wide-span">
        <div className="section-head compact">
          <div>
            <p className="section-kicker">운영 액션</p>
            <h2>처리 상태 관리</h2>
          </div>
        </div>
        <DataState
          unauthorized
          unauthorizedMessage={`현재 상태: ${formatLifecycleStatus(event.status)}. 상태 변경·담당자 지정은 분석가/관리자 로그인 후 가능합니다.`}
        />
        <a className="ghost-button" href={`#/login?next=${encodeURIComponent(`/events/${event.id}`)}`}>
          로그인 하러 가기
        </a>
      </article>
    );
  }

  return (
    <article className="panel-card wide-span">
      <div className="section-head compact">
        <div>
          <p className="section-kicker">운영 액션</p>
          <h2>처리 상태 관리</h2>
        </div>
        <div className="current-status">
          현재 <StatusBadge status={event.status} />
          {event.statusChangedAt ? <small>{formatFullDate(event.statusChangedAt)}</small> : null}
        </div>
      </div>

      {event.resolutionReason ? (
        <p className="reason-note">해결 사유: {event.resolutionReason}</p>
      ) : null}
      {event.falsePositiveReason ? (
        <p className="reason-note">오탐 사유: {event.falsePositiveReason}</p>
      ) : null}

      <form className="workflow-form" onSubmit={handleSubmit}>
        <fieldset className="status-actions" disabled={saving}>
          <legend>변경할 상태</legend>
          {LIFECYCLE_STATUS_ORDER.map((status) => (
            <button
              key={status}
              type="button"
              className={`status-choice ${targetStatus === status ? "selected" : ""} ${
                status === "FALSE_POSITIVE" ? "danger-ish" : ""
              }`}
              aria-pressed={targetStatus === status}
              onClick={() => {
                setTargetStatus(status);
                setFormError(null);
                setSuccess(null);
              }}
            >
              {formatLifecycleStatus(status)}
            </button>
          ))}
        </fieldset>

        {reasonField === "resolutionReason" ? (
          <label className="workflow-field">
            해결 사유 <em className="required-mark">(필수, 500자 이내)</em>
            <textarea
              value={resolutionReason}
              maxLength={500}
              rows={2}
              placeholder="예: 거래소 내부 이동으로 확인되어 정상 처리"
              onChange={(evt) => setResolutionReason(evt.target.value)}
            />
          </label>
        ) : null}

        {reasonField === "falsePositiveReason" ? (
          <label className="workflow-field">
            오탐 사유 <em className="required-mark">(필수, 500자 이내)</em>
            <textarea
              value={falsePositiveReason}
              maxLength={500}
              rows={2}
              placeholder="예: 화이트리스트 지갑 간 정기 리밸런싱으로 확인"
              onChange={(evt) => setFalsePositiveReason(evt.target.value)}
            />
          </label>
        ) : null}

        <div className="workflow-row">
          <label className="workflow-field">
            <span className="workflow-field-head">
              담당자 <em>(비우면 담당자 해제)</em>
              {currentUser && assignee.toLowerCase() !== currentUser.username.toLowerCase() ? (
                <button
                  type="button"
                  className="inline-link-button"
                  onClick={() => setAssignee(currentUser.username)}
                >
                  나에게 할당
                </button>
              ) : null}
            </span>
            <input
              type="text"
              value={assignee}
              maxLength={100}
              placeholder="예: alice"
              onChange={(evt) => setAssignee(evt.target.value)}
            />
          </label>
          <label className="workflow-field notes-field">
            운영 메모 <em>(2000자 이내)</em>
            <textarea
              value={notes}
              maxLength={2000}
              rows={2}
              placeholder="조사 내용, 참고 링크 등"
              onChange={(evt) => setNotes(evt.target.value)}
            />
          </label>
        </div>

        {formError ? <div className="banner error">{formError}</div> : null}
        {success ? <div className="banner success">{success}</div> : null}

        <div className="workflow-submit">
          <button type="submit" className="primary-button" disabled={saving}>
            {saving ? "저장 중..." : "상태 변경 저장"}
          </button>
        </div>
      </form>
    </article>
  );
}

function resolveActionError(cause: unknown, fallback: string): string {
  if (cause instanceof ApiError && cause.status === 401) {
    return "인증이 만료되었습니다. 관리자 페이지에서 다시 로그인해주세요.";
  }
  if (cause instanceof ApiError && cause.status === 403) {
    return "이 작업을 수행할 권한이 없습니다.";
  }
  if (cause instanceof ApiError && cause.serverMessage) {
    return cause.serverMessage;
  }
  return fallback;
}
