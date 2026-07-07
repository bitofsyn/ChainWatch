import type { AgentTeamStatus } from "../types";
import { TEAM_STATUS_LABELS } from "../lib/agentConsole";

interface TeamStatusBadgeProps {
  status: AgentTeamStatus;
}

export function TeamStatusBadge({ status }: TeamStatusBadgeProps) {
  return <span className={`status-pill team-${status}`}>{TEAM_STATUS_LABELS[status]}</span>;
}
