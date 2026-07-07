export type EventStatus = "critical" | "high" | "elevated";

export type EventLifecycleStatus = "NEW" | "ACKNOWLEDGED" | "INVESTIGATING" | "RESOLVED";

export interface DetectionEventItem {
  id: number;
  eventType: string;
  riskLevel: string;
  riskScore: number;
  summary: string;
  walletAddress: string;
  txHash: string | null;
  detectedAt: string;
  status: EventLifecycleStatus;
}

export interface DetectionEventPage {
  content: DetectionEventItem[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export interface AiAnalysisReport {
  id: number;
  status: string;
  provider: string | null;
  model: string | null;
  promptSummary: string | null;
  report: string | null;
  analyzedAt: string | null;
}

export interface DetectionEventDetail extends DetectionEventItem {
  transactionId: number | null;
  aiReport: AiAnalysisReport | null;
}

export interface TransactionItem {
  id: number;
  txHash: string;
  fromAddress: string;
  toAddress: string | null;
  amount: number;
  gasFee: number;
  blockNumber: number;
  timestamp: string;
  contractAddress: string | null;
}

export interface TransactionPage {
  content: TransactionItem[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export interface FeedEventItem {
  eventId: number;
  eventType: string;
  riskLevel: string;
  riskScore: number;
  summary: string;
  walletAddress: string;
  txHash: string | null;
  detectedAt: string;
}

export interface FeedTransactionItem {
  transactionId: number | null;
  txHash: string;
  fromAddress: string;
  toAddress: string | null;
  amount: number;
  gasFee: number;
  blockNumber: number;
  timestamp: string;
  contractAddress: string | null;
}

export interface HealthResponse {
  service: string;
  status: string;
  timestamp: string;
}

export interface LoginResult {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

export interface CollectorState {
  lastCollectedBlock: number;
}

export interface CollectorResult {
  blockNumber: number;
  transactionCount: number;
  savedTransactionCount: number;
  network: string;
  provider: string;
}

export interface KeyCount {
  key: string;
  count: number;
}

export interface WalletEventCount {
  walletAddress: string;
  eventCount: number;
  maxRiskScore: number;
  lastDetectedAt: string | null;
}

export interface EventStats {
  totalEvents: number;
  last24hEvents: number;
  riskLevelCounts: KeyCount[];
  eventTypeCounts: KeyCount[];
  statusCounts: KeyCount[];
  topWallets: WalletEventCount[];
}

export interface WalletSummary {
  walletAddress: string;
  eventCount: number;
  maxRiskScore: number;
  firstDetectedAt: string | null;
  lastDetectedAt: string | null;
  eventTypeCounts: KeyCount[];
  riskLevelCounts: KeyCount[];
}

export interface PipelineComponent {
  name: string;
  status: "UP" | "DOWN" | "DISABLED";
  detail: string;
}

export interface PipelineStatus {
  checkedAt: string;
  components: PipelineComponent[];
}

export interface DetectionRule {
  eventType: string;
  name: string;
  description: string;
  threshold: string;
  baseRiskLevel: string;
  baseRiskScore: number;
  active: boolean;
}

export interface DetectionRules {
  mode: string;
  rules: DetectionRule[];
}

/* ── AI Agent 팀 운영 콘솔 ─────────────────────── */

export type AgentTeamStatus = "healthy" | "degraded" | "blocked";

export type AgentTaskOutcome = "success" | "failed" | "retrying" | "in_progress";

export type AgentHandoffResult = "accepted" | "queued" | "rejected" | "completed";

export type SubAgentState = "idle" | "working" | "error";

export interface AgentQueueMetric {
  queued: number;
  inProgress: number;
  retrying: number;
  failedLastHour: number;
  oldestWaitingSeconds: number;
}

export interface AgentSlaTarget {
  metric: string;
  target: string;
  current: string;
  met: boolean;
}

export interface SubAgent {
  id: string;
  name: string;
  role: string;
  state: SubAgentState;
  currentTask: string | null;
}

export interface AgentTaskRecord {
  id: string;
  title: string;
  outcome: AgentTaskOutcome;
  startedAt: string;
  durationMs: number | null;
  detail: string;
}

export interface AgentTeam {
  id: string;
  name: string;
  role: string;
  description: string;
  status: AgentTeamStatus;
  statusReason: string | null;
  queue: AgentQueueMetric;
  successRate1h: number;
  avgProcessingMs: number;
  throughputPerHour: number;
  lastHandoffTo: string | null;
  inputTypes: string[];
  outputTypes: string[];
  subAgents: SubAgent[];
  recentTasks: AgentTaskRecord[];
  recentFailures: AgentTaskRecord[];
  slaTargets: AgentSlaTarget[];
  updatedAt: string;
}

export interface AgentHandoffEvent {
  id: string;
  fromTeamId: string;
  toTeamId: string;
  subject: string;
  reason: string;
  result: AgentHandoffResult;
  occurredAt: string;
}

export interface AgentOpsAlert {
  id: string;
  severity: "critical" | "warning";
  message: string;
  teamId: string | null;
  raisedAt: string;
}

export interface AgentOpsOverview {
  generatedAt: string;
  activeTeams: number;
  totalTeams: number;
  throughputPerHour: number;
  avgResponseMs: number;
  failed1h: number;
  retried1h: number;
  bottleneckTeamId: string | null;
  alerts: AgentOpsAlert[];
}

export interface AgentOpsSnapshot {
  overview: AgentOpsOverview;
  teams: AgentTeam[];
  handoffs: AgentHandoffEvent[];
  /** 프론트 데이터 계층에서 채움: 실제 API 응답인지 mock 폴백인지 */
  source?: "api" | "mock";
}
