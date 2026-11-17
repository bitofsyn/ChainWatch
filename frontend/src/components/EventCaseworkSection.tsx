import { useCallback, useEffect, useState, type FormEvent } from "react";
import { addEventComment, ApiError, fetchEventActivities, proposeExcludedAddress } from "../api";
import { useAuth } from "../contexts/AuthContext";
import { formatFullDate, shortenAddress } from "../lib/format";
import type { DetectionEventItem, EventActivity, EventActivityType } from "../types";

const ACTIVITY_LABELS: Record<EventActivityType, string> = {
  STATUS: "상태 변경",
  ASSIGNMENT: "담당자 변경",
  COMMENT: "코멘트"
};

function messageOf(cause: unknown, fallback: string): string {
  return cause instanceof ApiError && cause.message ? cause.message : fallback;
}

/**
 * 이벤트 처리 이력. 상태·담당자 변경과 코멘트를 최신순으로 보여주고,
 * 분석가·관리자는 코멘트를 남기거나 이 지갑을 탐지 예외로 제안할 수 있다.
 */
export function EventCaseworkSection({ event }: { event: DetectionEventItem }) {
  const { canOperate } = useAuth();
  const [activities, setActivities] = useState<EventActivity[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [comment, setComment] = useState("");
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try {
      setActivities(await fetchEventActivities(event.id));
      setError(null);
    } catch (cause) {
      setError(messageOf(cause, "처리 이력을 불러오지 못했습니다."));
    } finally {
      setLoading(false);
    }
  }, [event.id]);

  // 상태·담당자·메모가 바뀌면(처리 폼 저장 직후) 이력을 다시 읽는다.
  useEffect(() => {
    load();
  }, [load, event.status, event.assignee, event.notes, event.statusChangedAt]);

  async function handleComment(formEvent: FormEvent) {
    formEvent.preventDefault();
    if (!comment.trim()) {
      return;
    }
    setSaving(true);
    try {
      await addEventComment(event.id, comment.trim());
      setComment("");
      await load();
    } catch (cause) {
      setError(messageOf(cause, "코멘트를 저장하지 못했습니다."));
    } finally {
      setSaving(false);
    }
  }

  return (
    <article className="panel-card wide-span">
      <div className="section-head compact">
        <div>
          <p className="section-kicker">처리 이력</p>
          <h2>누가 언제 무엇을 했는지</h2>
        </div>
      </div>

      {canOperate ? (
        <form className="comment-form" onSubmit={handleComment}>
          <label className="visually-hidden" htmlFor={`comment-${event.id}`}>
            코멘트
          </label>
          <textarea
            id={`comment-${event.id}`}
            value={comment}
            onChange={(evt) => setComment(evt.target.value)}
            placeholder="조사 내용이나 다음 담당자에게 남길 말을 적습니다. 이전 코멘트는 지워지지 않습니다."
            maxLength={2000}
            rows={2}
          />
          <button type="submit" className="ghost-button" disabled={saving || !comment.trim()}>
            {saving ? "저장 중…" : "코멘트 남기기"}
          </button>
        </form>
      ) : null}

      {error ? (
        <div className="banner error" role="alert">
          {error}
        </div>
      ) : null}

      {loading ? (
        <p className="cell-muted">처리 이력을 불러오는 중…</p>
      ) : activities.length === 0 ? (
        <p className="cell-muted">아직 처리 이력이 없습니다. 상태를 바꾸거나 코멘트를 남기면 여기에 쌓입니다.</p>
      ) : (
        <ol className="activity-list">
          {activities.map((activity) => (
            <li key={activity.id} className={`activity-item activity-${activity.type.toLowerCase()}`}>
              <div className="activity-meta">
                <strong>{activity.actor}</strong>
                <span>{ACTIVITY_LABELS[activity.type] ?? activity.type}</span>
                <time dateTime={activity.createdAt}>{formatFullDate(activity.createdAt)}</time>
              </div>
              <p>{activity.detail}</p>
            </li>
          ))}
        </ol>
      )}

      {canOperate ? <ExclusionProposal event={event} onProposed={load} /> : null}
    </article>
  );
}

/** 오탐으로 판단한 지갑을 이후 탐지에서 제외해 달라고 관리자에게 제안한다. */
function ExclusionProposal({ event, onProposed }: { event: DetectionEventItem; onProposed: () => void }) {
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [saving, setSaving] = useState(false);
  const [result, setResult] = useState<{ ok: boolean; text: string } | null>(null);

  async function handleSubmit(formEvent: FormEvent) {
    formEvent.preventDefault();
    setSaving(true);
    setResult(null);
    try {
      await proposeExcludedAddress(event.walletAddress, reason.trim(), event.id);
      setResult({ ok: true, text: "관리자에게 제안했습니다. 승인되면 이 지갑은 탐지에서 제외됩니다." });
      setReason("");
      setOpen(false);
      onProposed();
    } catch (cause) {
      setResult({ ok: false, text: messageOf(cause, "제안을 저장하지 못했습니다.") });
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="exclusion-proposal">
      <div className="exclusion-head">
        <div>
          <strong>탐지 기준에 반영하기</strong>
          <p className="cell-muted">
            이 지갑({shortenAddress(event.walletAddress)})이 거래소 지갑이나 정기 지급 주소처럼 정상 활동이라면, 탐지
            예외로 등록해 달라고 관리자에게 제안할 수 있습니다.
          </p>
        </div>
        {!open ? (
          <button type="button" className="ghost-button" onClick={() => setOpen(true)}>
            예외 주소 제안
          </button>
        ) : null}
      </div>
      {open ? (
        <form className="comment-form" onSubmit={handleSubmit}>
          <label className="visually-hidden" htmlFor={`exclusion-${event.id}`}>
            제안 사유
          </label>
          <textarea
            id={`exclusion-${event.id}`}
            value={reason}
            onChange={(evt) => setReason(evt.target.value)}
            placeholder="예: 거래소 핫월렛으로 확인됨. 출금 배치로 수신자가 많음."
            maxLength={500}
            rows={2}
            required
          />
          <div className="form-actions">
            <button type="button" className="ghost-button" onClick={() => setOpen(false)}>
              취소
            </button>
            <button type="submit" className="primary-button" disabled={saving || !reason.trim()}>
              {saving ? "제안 중…" : "관리자에게 제안"}
            </button>
          </div>
        </form>
      ) : null}
      {result ? (
        <div className={`banner ${result.ok ? "success" : "error"}`} role="status">
          {result.text}
        </div>
      ) : null}
    </div>
  );
}
