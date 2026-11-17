import { useCallback, useEffect, useState } from "react";
import { ApiError, fetchWorkQueue, updateEventStatusInBulk } from "../api";
import { RiskBadge } from "../components/RiskBadge";
import { StatusBadge } from "../components/StatusBadge";
import { formatDate, formatEventType, shortenAddress } from "../lib/format";
import { formatAge, formatNumber } from "../lib/opsOverview";
import type { DetectionEventItem, WorkQueue } from "../types";

function ageSeconds(iso: string | null | undefined): number | null {
  if (!iso) {
    return null;
  }
  return Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000));
}

/**
 * 분석가의 첫 화면. "내가 맡은 건"과 "아직 아무도 맡지 않은 고위험 건"을
 * 위험 점수가 높고 오래 기다린 순서로 보여줘, 오늘 무엇부터 할지 정하게 한다.
 */
export function WorkQueuePage() {
  const [queue, setQueue] = useState<WorkQueue | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [claiming, setClaiming] = useState<number | null>(null);

  const load = useCallback(async () => {
    try {
      setQueue(await fetchWorkQueue());
      setError(null);
    } catch (cause) {
      setError(cause instanceof ApiError && cause.message ? cause.message : "작업 큐를 불러오지 못했습니다.");
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  /** 미할당 건을 내 케이스로 가져오면서 접수 상태로 바꾼다. */
  async function claim(event: DetectionEventItem) {
    if (!queue) {
      return;
    }
    setClaiming(event.id);
    try {
      await updateEventStatusInBulk({ ids: [event.id], status: "ACKNOWLEDGED", assignee: queue.username });
      await load();
    } catch (cause) {
      setError(cause instanceof ApiError && cause.message ? cause.message : "이벤트를 가져오지 못했습니다.");
    } finally {
      setClaiming(null);
    }
  }

  return (
    <>
      <section className="page-head">
        <div>
          <p className="eyebrow">Work queue</p>
          <h1>내 작업 큐</h1>
          <p className="page-lede">
            맡은 이벤트를 먼저 처리하고, 아무도 맡지 않은 고위험 이벤트는 가져와서 조사합니다. 위험 점수가 높고 오래
            기다린 순서로 정렬됩니다.
          </p>
        </div>
        {error ? (
          <div className="banner error" role="alert">
            {error}
          </div>
        ) : null}
      </section>

      <section className="kpi-grid">
        <article className="metric-card">
          <span>내가 맡은 미완료</span>
          <strong>{queue ? formatNumber(queue.myOpenCount) : "-"}</strong>
          <small>신규·접수·조사중</small>
        </article>
        <article className="metric-card">
          <span>미할당 고위험</span>
          <strong className={queue && queue.unassignedHighRiskCount > 0 ? "text-critical" : ""}>
            {queue ? formatNumber(queue.unassignedHighRiskCount) : "-"}
          </strong>
          <small>치명·높음 등급, 담당자 없음</small>
        </article>
        <article className="metric-card">
          <span>최장 대기</span>
          <strong className="metric-date">
            {queue ? formatAge(ageSeconds(queue.oldestUnassignedDetectedAt)) : "-"}
          </strong>
          <small>미할당 고위험 중 가장 오래된 건</small>
        </article>
      </section>

      <QueueTable
        title="내 케이스"
        kicker="먼저 처리할 것"
        events={queue?.myCases ?? []}
        total={queue?.myOpenCount ?? 0}
        loading={queue == null && !error}
        emptyMessage="맡은 이벤트가 없습니다. 아래 미할당 고위험에서 가져오세요."
        moreHref={queue ? `#/events?assignee=${encodeURIComponent(queue.username)}` : undefined}
      />

      <QueueTable
        title="미할당 고위험"
        kicker="아직 아무도 맡지 않음"
        events={queue?.unassignedHighRisk ?? []}
        total={queue?.unassignedHighRiskCount ?? 0}
        loading={queue == null && !error}
        emptyMessage="담당자가 없는 고위험 이벤트가 없습니다."
        moreHref="#/events?unassigned=true"
        action={(event) => (
          <button
            type="button"
            className="ghost-button"
            onClick={() => claim(event)}
            disabled={claiming != null}
          >
            {claiming === event.id ? "가져오는 중…" : "내가 맡기"}
          </button>
        )}
      />
    </>
  );
}

interface QueueTableProps {
  title: string;
  kicker: string;
  events: DetectionEventItem[];
  total: number;
  loading: boolean;
  emptyMessage: string;
  moreHref?: string;
  action?: (event: DetectionEventItem) => JSX.Element;
}

function QueueTable({ title, kicker, events, total, loading, emptyMessage, moreHref, action }: QueueTableProps) {
  return (
    <section className="panel-card">
      <div className="section-head compact">
        <div>
          <p className="section-kicker">{kicker}</p>
          <h2>
            {title} <span className="cell-muted">{formatNumber(total)}건</span>
          </h2>
        </div>
        {moreHref && total > events.length ? (
          <a className="ghost-button" href={moreHref}>
            전체 보기
          </a>
        ) : null}
      </div>
      {loading ? (
        <p className="cell-muted">불러오는 중…</p>
      ) : events.length === 0 ? (
        <p className="cell-muted">{emptyMessage}</p>
      ) : (
        <div className="table-scroll">
          <table className="data-table" aria-label={title}>
            <thead>
              <tr>
                <th scope="col">위험</th>
                <th scope="col">상태</th>
                <th scope="col">유형</th>
                <th scope="col">지갑</th>
                <th scope="col">대기</th>
                <th scope="col">탐지 시각</th>
                {action ? <th scope="col">처리</th> : null}
              </tr>
            </thead>
            <tbody>
              {events.map((event) => (
                <tr key={event.id}>
                  <td>
                    <RiskBadge riskLevel={event.riskLevel} riskScore={event.riskScore} />{" "}
                    <span className="strong-num">{event.riskScore}</span>
                  </td>
                  <td>
                    <StatusBadge status={event.status} />
                  </td>
                  <td>
                    <a className="row-link" href={`#/events/${event.id}`} title={event.summary}>
                      {formatEventType(event.eventType)} #{event.id}
                    </a>
                  </td>
                  <td className="mono" title={event.walletAddress}>
                    <a className="row-link" href={`#/wallets/${event.walletAddress}`}>
                      {shortenAddress(event.walletAddress)}
                    </a>
                  </td>
                  <td>{formatAge(ageSeconds(event.detectedAt))}</td>
                  <td className="cell-time">{formatDate(event.detectedAt)}</td>
                  {action ? <td>{action(event)}</td> : null}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
