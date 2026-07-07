import { startTransition, useEffect, useState } from "react";
import type { AgentOpsSnapshot, AgentTeam } from "../types";
import {
  fetchAgentOpsSnapshot,
  formatDurationMs,
  formatWaitSeconds,
  HANDOFF_RESULT_LABELS,
  successRateClass,
  teamNameOf
} from "../lib/agentConsole";
import { formatDate } from "../lib/format";
import { TeamStatusBadge } from "../components/TeamStatusBadge";
import { AgentOpsSubNav } from "../components/AgentOpsSubNav";

function TeamCard({ team, teams }: { team: AgentTeam; teams: AgentTeam[] }) {
  return (
    <a className={`glass-card team-card ${team.status}`} href={`#/agents/teams/${team.id}`}>
      <div className="team-card-head">
        <div>
          <h2>{team.name}</h2>
          <p className="team-role">{team.role}</p>
        </div>
        <TeamStatusBadge status={team.status} />
      </div>

      {team.statusReason ? <p className="team-reason">{team.statusReason}</p> : null}

      <div className="queue-metrics">
        <div>
          <span>대기 큐</span>
          <strong>{team.queue.queued}</strong>
        </div>
        <div>
          <span>처리 중</span>
          <strong>{team.queue.inProgress}</strong>
        </div>
        <div>
          <span>재시도</span>
          <strong className={team.queue.retrying > 0 ? "text-high" : ""}>
            {team.queue.retrying}
          </strong>
        </div>
        <div>
          <span>1시간 실패</span>
          <strong className={team.queue.failedLastHour > 0 ? "text-critical" : ""}>
            {team.queue.failedLastHour}
          </strong>
        </div>
      </div>

      <div className="team-meter">
        <div className="team-meter-label">
          <span>최근 1시간 성공률</span>
          <strong>{team.successRate1h}%</strong>
        </div>
        <div className="bar-track">
          <div
            className={`bar-fill ${successRateClass(team.successRate1h)}`}
            style={{ width: `${Math.min(team.successRate1h, 100)}%` }}
          />
        </div>
      </div>

      <div className="team-foot">
        <span>평균 처리 {formatDurationMs(team.avgProcessingMs)}</span>
        <span>시간당 {team.throughputPerHour}건</span>
        <span>최근 핸드오프 → {teamNameOf(teams, team.lastHandoffTo)}</span>
      </div>
    </a>
  );
}

export function AgentConsolePage() {
  const [snapshot, setSnapshot] = useState<AgentOpsSnapshot | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    const load = () => {
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
            setError("Agent 운영 현황을 불러오지 못했습니다.");
            setLoading(false);
          });
        });
    };

    load();
    const timer = setInterval(load, 30_000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, []);

  const overview = snapshot?.overview ?? null;
  const teams = snapshot?.teams ?? [];
  const bottleneck = teams.find((team) => team.id === overview?.bottleneckTeamId) ?? null;
  const recentHandoffs = (snapshot?.handoffs ?? []).slice(0, 6);

  return (
    <>
      <section className="page-head">
        <div>
          <p className="eyebrow">Agent Ops</p>
          <h1>AI Agent 팀 관제</h1>
          <p className="page-lede">
            탐지 → 분석 → 등급 산정 → 알림으로 이어지는 Agent 팀 파이프라인의 큐, 처리량,
            핸드오프 상태를 팀 단위로 감시합니다. 병목과 차단 상태를 우선 확인하세요.
          </p>
        </div>
        {error ? <div className="banner error">{error}</div> : null}
      </section>

      <AgentOpsSubNav active="board" />

      {snapshot?.source === "mock" ? (
        <div className="banner warn ops-alert">
          백엔드 agent-ops API에 연결하지 못해 mock 데이터를 표시 중입니다. (30초마다 재시도)
        </div>
      ) : null}

      {(overview?.alerts ?? []).map((alert) => (
        <div
          key={alert.id}
          className={`banner ops-alert ${alert.severity === "critical" ? "error" : "warn"}`}
        >
          <strong>{alert.severity === "critical" ? "긴급" : "주의"}</strong> {alert.message}
          <small> · {formatDate(alert.raisedAt)}</small>
        </div>
      ))}

      <section className="kpi-grid">
        <article className="metric-card">
          <span>활성 팀</span>
          <strong>{loading ? "-" : `${overview?.activeTeams ?? 0}/${overview?.totalTeams ?? 0}`}</strong>
          <small>차단 상태 제외</small>
        </article>
        <article className="metric-card">
          <span>전체 처리량</span>
          <strong>{loading ? "-" : overview?.throughputPerHour ?? 0}</strong>
          <small>건/시간 (팀 합산)</small>
        </article>
        <article className="metric-card">
          <span>평균 응답 시간</span>
          <strong>{loading || !overview ? "-" : formatDurationMs(overview.avgResponseMs)}</strong>
          <small>팀 평균 처리 시간</small>
        </article>
        <article className="metric-card">
          <span>실패 / 재시도</span>
          <strong className={overview && overview.failed1h > 0 ? "text-critical" : ""}>
            {loading ? "-" : `${overview?.failed1h ?? 0} / ${overview?.retried1h ?? 0}`}
          </strong>
          <small>최근 1시간</small>
        </article>
        <article className="metric-card">
          <span>현재 병목</span>
          <strong className={bottleneck ? "text-high" : ""}>
            {loading ? "-" : bottleneck?.name ?? "없음"}
          </strong>
          <small>
            {bottleneck
              ? `대기 ${bottleneck.queue.queued}건 · 최장 ${formatWaitSeconds(bottleneck.queue.oldestWaitingSeconds)}`
              : "대기 적체 없음"}
          </small>
        </article>
      </section>

      {loading ? <div className="empty-state">Agent 팀 상태를 불러오는 중...</div> : null}

      <section className="team-board">
        {teams.map((team) => (
          <TeamCard key={team.id} team={team} teams={teams} />
        ))}
      </section>

      <section className="glass-card handoff-panel">
        <div className="section-head">
          <div>
            <p className="section-kicker">핸드오프 흐름</p>
            <h2>최근 팀 간 작업 이관</h2>
          </div>
          <a className="ghost-button" href="#/agents/activity">
            전체 로그
          </a>
        </div>
        <div className="table-wrap">
          {recentHandoffs.length === 0 && !loading ? (
            <div className="empty-state">최근 핸드오프 내역이 없습니다.</div>
          ) : null}
          {recentHandoffs.map((handoff) => (
            <div className="handoff-row" key={handoff.id}>
              <div className="handoff-route">
                <span>{teamNameOf(teams, handoff.fromTeamId)}</span>
                <span className="handoff-arrow" aria-hidden="true">
                  →
                </span>
                <span>{teamNameOf(teams, handoff.toTeamId)}</span>
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
