import { startTransition, useEffect, useState } from "react";
import type { AgentOpsSnapshot } from "../types";
import { fetchAgentOpsSnapshot, HANDOFF_RESULT_LABELS, teamNameOf } from "../lib/agentConsole";
import { formatDate } from "../lib/format";
import { AgentOpsSubNav } from "../components/AgentOpsSubNav";

interface RouteCount {
  key: string;
  fromTeamId: string;
  toTeamId: string;
  count: number;
}

export function AgentActivityPage() {
  const [snapshot, setSnapshot] = useState<AgentOpsSnapshot | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    fetchAgentOpsSnapshot()
      .then((data) => {
        if (!active) {
          return;
        }
        startTransition(() => {
          setSnapshot(data);
          setError(null);
          setLoading(false);
        });
      })
      .catch(() => {
        if (!active) {
          return;
        }
        startTransition(() => {
          setError("핸드오프 로그를 불러오지 못했습니다.");
          setLoading(false);
        });
      });

    return () => {
      active = false;
    };
  }, []);

  const teams = snapshot?.teams ?? [];
  const handoffs = [...(snapshot?.handoffs ?? [])].sort((a, b) =>
    b.occurredAt.localeCompare(a.occurredAt)
  );

  const routes = handoffs
    .reduce<RouteCount[]>((acc, handoff) => {
      const key = `${handoff.fromTeamId}→${handoff.toTeamId}`;
      const existing = acc.find((route) => route.key === key);
      if (existing) {
        existing.count += 1;
      } else {
        acc.push({ key, fromTeamId: handoff.fromTeamId, toTeamId: handoff.toTeamId, count: 1 });
      }
      return acc;
    }, [])
    .sort((a, b) => b.count - a.count);

  return (
    <>
      <section className="page-head">
        <div>
          <p className="eyebrow">Agent Ops</p>
          <h1>핸드오프 / 활동 로그</h1>
          <p className="page-lede">
            팀 간 작업 이관 내역을 시간순으로 추적합니다. 반려·대기가 몰리는 경로가 병목의
            신호입니다.
          </p>
        </div>
        {error ? <div className="banner error">{error}</div> : null}
      </section>

      <AgentOpsSubNav active="activity" />

      {loading ? <div className="empty-state">핸드오프 로그를 불러오는 중...</div> : null}

      {routes.length > 0 ? (
        <section className="route-summary">
          {routes.map((route) => (
            <article className="glass-card route-card" key={route.key}>
              <div className="handoff-route">
                <span>{teamNameOf(teams, route.fromTeamId)}</span>
                <span className="handoff-arrow" aria-hidden="true">
                  →
                </span>
                <span>{teamNameOf(teams, route.toTeamId)}</span>
              </div>
              <strong>{route.count}건</strong>
            </article>
          ))}
        </section>
      ) : null}

      <section className="glass-card">
        <div className="section-head">
          <div>
            <p className="section-kicker">시간순 로그</p>
            <h2>최근 핸드오프 {handoffs.length}건</h2>
          </div>
        </div>
        <div className="table-wrap">
          {handoffs.length === 0 && !loading ? (
            <div className="empty-state">기록된 핸드오프가 없습니다.</div>
          ) : null}
          {handoffs.map((handoff) => (
            <div className="handoff-row" key={handoff.id}>
              <div className="handoff-route">
                <a href={`#/agents/teams/${handoff.fromTeamId}`}>
                  {teamNameOf(teams, handoff.fromTeamId)}
                </a>
                <span className="handoff-arrow" aria-hidden="true">
                  →
                </span>
                <a href={`#/agents/teams/${handoff.toTeamId}`}>
                  {teamNameOf(teams, handoff.toTeamId)}
                </a>
              </div>
              <div className="handoff-body">
                <strong>{handoff.subject}</strong>
                <small>{handoff.reason}</small>
              </div>
              <div className="handoff-meta">
                <span className={`status-pill handoff-${handoff.result}`}>
                  {HANDOFF_RESULT_LABELS[handoff.result]}
                </span>
                <small>{formatDate(handoff.occurredAt)}</small>
              </div>
            </div>
          ))}
        </div>
      </section>
    </>
  );
}
