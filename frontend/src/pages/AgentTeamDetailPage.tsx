import { startTransition, useEffect, useState } from "react";
import type { AgentOpsSnapshot, AgentTaskRecord, AgentTeam, SubAgentState } from "../types";
import {
  fetchAgentOpsSnapshot,
  formatDurationMs,
  formatWaitSeconds,
  SUBAGENT_STATE_LABELS,
  TASK_OUTCOME_LABELS,
  teamNameOf
} from "../lib/agentConsole";
import { formatDate } from "../lib/format";
import { TeamStatusBadge } from "../components/TeamStatusBadge";

interface AgentTeamDetailPageProps {
  teamId: string;
}

const SUBAGENT_DOT: Record<SubAgentState, string> = {
  working: "online",
  idle: "idle",
  error: "error"
};

function TaskTimeline({ tasks, emptyMessage }: { tasks: AgentTaskRecord[]; emptyMessage: string }) {
  if (tasks.length === 0) {
    return <div className="empty-state">{emptyMessage}</div>;
  }
  return (
    <div className="ops-timeline">
      {tasks.map((task) => (
        <div className={`timeline-item ${task.outcome}`} key={task.id}>
          <div className="timeline-head">
            <strong>{task.title}</strong>
            <span className={`status-pill task-${task.outcome}`}>
              {TASK_OUTCOME_LABELS[task.outcome]}
            </span>
          </div>
          <p>{task.detail}</p>
          <small>
            {formatDate(task.startedAt)}
            {task.durationMs != null ? ` · 소요 ${formatDurationMs(task.durationMs)}` : ""}
          </small>
        </div>
      ))}
    </div>
  );
}

export function AgentTeamDetailPage({ teamId }: AgentTeamDetailPageProps) {
  const [snapshot, setSnapshot] = useState<AgentOpsSnapshot | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);

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
          setError("팀 상세 정보를 불러오지 못했습니다.");
          setLoading(false);
        });
      });

    return () => {
      active = false;
    };
  }, [teamId]);

  const teams = snapshot?.teams ?? [];
  const team: AgentTeam | null = teams.find((item) => item.id === teamId) ?? null;

  if (loading) {
    return <div className="empty-state">팀 정보를 불러오는 중...</div>;
  }

  if (error || !team) {
    return (
      <section className="page-head">
        <div>
          <a className="back-link" href="#/agents">
            ← Agent 콘솔로 돌아가기
          </a>
          <p className="eyebrow">Agent Ops</p>
          <h1>팀을 찾을 수 없습니다</h1>
          <p className="page-lede">{error ?? `"${teamId}" 팀이 존재하지 않습니다.`}</p>
        </div>
      </section>
    );
  }

  return (
    <>
      <section className="page-head">
        <div>
          <a className="back-link" href="#/agents">
            ← Agent 콘솔로 돌아가기
          </a>
          <div className="badge-group">
            <TeamStatusBadge status={team.status} />
            <span className="status-pill elevated">{team.role}</span>
          </div>
          <h1>{team.name}</h1>
          <p className="page-lede">{team.description}</p>
        </div>
      </section>

      {team.statusReason ? (
        <div className={`banner ops-alert ${team.status === "blocked" ? "error" : "warn"}`}>
          {team.statusReason}
        </div>
      ) : null}

      <section className="kpi-grid">
        <article className="metric-card">
          <span>대기 큐</span>
          <strong>{team.queue.queued}</strong>
          <small>최장 대기 {formatWaitSeconds(team.queue.oldestWaitingSeconds)}</small>
        </article>
        <article className="metric-card">
          <span>처리 중</span>
          <strong>{team.queue.inProgress}</strong>
          <small>재시도 {team.queue.retrying}건</small>
        </article>
        <article className="metric-card">
          <span>1시간 성공률</span>
          <strong className={team.successRate1h < 95 ? "text-high" : ""}>
            {team.successRate1h}%
          </strong>
          <small>실패 {team.queue.failedLastHour}건</small>
        </article>
        <article className="metric-card">
          <span>평균 처리 시간</span>
          <strong>{formatDurationMs(team.avgProcessingMs)}</strong>
          <small>작업당</small>
        </article>
        <article className="metric-card">
          <span>처리량</span>
          <strong>{team.throughputPerHour}</strong>
          <small>건/시간 · 핸드오프 → {teamNameOf(teams, team.lastHandoffTo)}</small>
        </article>
      </section>

      <section className="grid-panel">
        <article className="glass-card wide">
          <div className="section-head">
            <div>
              <p className="section-kicker">작업 타임라인</p>
              <h2>최근 작업 내역</h2>
            </div>
          </div>
          <TaskTimeline tasks={team.recentTasks} emptyMessage="최근 작업 내역이 없습니다." />
        </article>

        <article className="glass-card">
          <div className="section-head compact">
            <div>
              <p className="section-kicker">구성</p>
              <h2>서브 에이전트 {team.subAgents.length}개</h2>
            </div>
          </div>
          <ul className="pulse-list subagent-list">
            {team.subAgents.map((agent) => (
              <li key={agent.id}>
                <span className={`dot ${SUBAGENT_DOT[agent.state]}`} aria-hidden="true" />
                <div>
                  <strong className="mono">{agent.name}</strong>
                  <span className="subagent-role">
                    {agent.role} · {SUBAGENT_STATE_LABELS[agent.state]}
                  </span>
                  {agent.currentTask ? <small>{agent.currentTask}</small> : null}
                </div>
              </li>
            ))}
          </ul>
        </article>

        <article className="glass-card">
          <div className="section-head compact">
            <div>
              <p className="section-kicker">SLA</p>
              <h2>목표 지표</h2>
            </div>
          </div>
          <div className="table-wrap">
            {team.slaTargets.map((sla) => (
              <div className="sla-row" key={sla.metric}>
                <div>
                  <strong>{sla.metric}</strong>
                  <small>목표 {sla.target}</small>
                </div>
                <span className={sla.met ? "" : "text-critical"}>{sla.current}</span>
                <span className={`status-pill ${sla.met ? "task-success" : "task-failed"}`}>
                  {sla.met ? "충족" : "미달"}
                </span>
              </div>
            ))}
          </div>
        </article>

        <article className="glass-card">
          <div className="section-head compact">
            <div>
              <p className="section-kicker">인터페이스</p>
              <h2>입력 / 출력 유형</h2>
            </div>
          </div>
          <div className="io-block">
            <span>입력</span>
            <div className="chip-list">
              {team.inputTypes.map((type) => (
                <span className="chip mono" key={type}>
                  {type}
                </span>
              ))}
            </div>
          </div>
          <div className="io-block">
            <span>출력</span>
            <div className="chip-list">
              {team.outputTypes.map((type) => (
                <span className="chip mono" key={type}>
                  {type}
                </span>
              ))}
            </div>
          </div>
        </article>

        <article className="glass-card wide-span">
          <div className="section-head compact">
            <div>
              <p className="section-kicker">실패 사례</p>
              <h2>최근 실패 작업</h2>
            </div>
          </div>
          <TaskTimeline
            tasks={team.recentFailures}
            emptyMessage="최근 실패한 작업이 없습니다."
          />
        </article>
      </section>
    </>
  );
}
