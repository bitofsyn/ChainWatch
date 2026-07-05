import { RISK_LEVEL_LABELS, toStatus } from "../lib/format";

interface RiskBadgeProps {
  riskLevel: string;
  riskScore: number;
}

export function RiskBadge({ riskLevel, riskScore }: RiskBadgeProps) {
  return (
    <span className={`status-pill ${toStatus(riskScore)}`}>
      {RISK_LEVEL_LABELS[riskLevel] ?? riskLevel.toLowerCase()}
    </span>
  );
}
