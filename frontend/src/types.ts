export type EventStatus = "critical" | "high" | "elevated";

export type EventLifecycleStatus =
  | "NEW"
  | "ACKNOWLEDGED"
  | "INVESTIGATING"
  | "RESOLVED"
  | "FALSE_POSITIVE";

export interface DetectionEventItem {
  id: number;
  eventType: string;
  riskLevel: string;
  riskScore: number;
  summary: string;
  walletAddress: string;
  txHash: string | null;
  /** 이벤트가 발생한 체인 (예: ethereum-mainnet). 레거시 이벤트도 기본 체인으로 채워진다. */
  network: string;
  detectedAt: string;
  status: EventLifecycleStatus;
  /* 분석가 workflow 필드 (Wave1 백엔드 계약, 전부 nullable) */
  assignee?: string | null;
  statusChangedAt?: string | null;
  resolutionReason?: string | null;
  falsePositiveReason?: string | null;
  notes?: string | null;
}

export interface DetectionEventPage {
  content: DetectionEventItem[];
  totalElements: number;
  totalPages: number;
  number: number;
}

/** PATCH /api/events/{id}/status 요청 본문 (Wave1 계약과 1:1) */
export interface EventStatusUpdateRequest {
  status: EventLifecycleStatus;
  /** null이면 기존 값 유지, ""이면 담당자 해제 */
  assignee?: string | null;
  /** status=RESOLVED일 때 필수, max 500 */
  resolutionReason?: string | null;
  /** status=FALSE_POSITIVE일 때 필수, max 500 */
  falsePositiveReason?: string | null;
  /** null이면 기존 값 유지, max 2000 */
  notes?: string | null;
}

export type AiConfidence = "low" | "medium" | "high";

export type AiEscalationLevel = "none" | "monitor" | "escalate" | "urgent";

export interface AiEvidenceItem {
  source: string;
  fact: string;
}

/** Wave1 AI 계약의 structuredAnalysis 객체 */
export interface AiStructuredAnalysis {
  riskSummary: string | null;
  evidence: AiEvidenceItem[];
  possibleScenarios: string[];
  recommendedActions: string[];
  confidence: AiConfidence | null;
  falsePositiveFactors: string[];
  escalationLevel: AiEscalationLevel | null;
}

export interface AiAnalysisReport {
  id: number;
  status: string;
  provider: string | null;
  model: string | null;
  promptSummary: string | null;
  report: string | null;
  analyzedAt: string | null;
  /** 구조화 분석 존재 여부. 구버전 리포트는 필드 자체가 없을 수 있어 optional */
  structured?: boolean;
  structuredAnalysis?: AiStructuredAnalysis | null;
}

export interface DetectionEventDetail extends DetectionEventItem {
  transactionId: number | null;
  aiReport: AiAnalysisReport | null;
  /** Wave2: 발화한 룰 버전. 레거시 이벤트는 null/부재 */
  ruleVersion?: string | null;
  /** Wave2: 룰 발화 근거 JSON (룰별 스키마 상이). 레거시/직렬화 실패 시 null */
  evidence?: Record<string, unknown> | null;
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
  /** Wave2: head - blockNumber + 1. 둘 다 null이면 head 미관측(판정 불가) */
  confirmations?: number | null;
  /** Wave2: confirmations >= confirmation-depth(기본 12)이면 true. null = 판정 불가 */
  confirmed?: boolean | null;
}

export interface TransactionPage {
  content: TransactionItem[];
  totalElements: number;
  totalPages: number;
  number: number;
}

/* ── 감사 로그 (ADMIN 전용, Wave1 계약) ─────────── */

export interface AuditLogItem {
  id: number;
  actor: string;
  role: string | null;
  action: string;
  targetType: string;
  targetId: string;
  detail: string | null;
  clientIp: string | null;
  createdAt: string;
}

export interface AuditLogPage {
  content: AuditLogItem[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
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

export type Role = "ADMIN" | "ANALYST";

export interface User {
  username: string;
  role: Role;
  displayName: string | null;
}

export interface LoginResult {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  refreshToken: string;
  user: User;
}

export interface UserAccountItem {
  id: number;
  username: string;
  role: Role;
  displayName: string | null;
  active: boolean;
  createdAt: string;
  lastLoginAt: string | null;
}

/** 사용자 생성/비밀번호 초기화 응답. initialPassword는 서버 생성 시 1회만 내려온다. */
export interface UserCreateResult {
  user: UserAccountItem;
  initialPassword: string | null;
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

/* ── 운영 Overview (GET /api/ops/overview) ─────── */

export type OpsCollectorHealth = "UP" | "DEGRADED" | "DOWN" | "UNKNOWN";

export interface OpsCollector {
  /** null = head 미관측(수집이 아직 돌지 않음) */
  chainHead: number | null;
  /** null = 수집 이력 없음 */
  lastCollectedBlock: number | null;
  /** null = 판정 불가 */
  lagBlocks: number | null;
  confirmationDepth: number;
  status: OpsCollectorHealth;
}

export interface OpsKpis {
  transactionsPerMinute: number;
  /** null = 직전 구간 0건이라 비교 불가 */
  transactionsDeltaPercent: number | null;
  /** null = 최근 창 수집 0건(분모 0) */
  detectionRatePercent: number | null;
  detectedLast5m: number;
  /** NEW(레거시 null 포함) + ACKNOWLEDGED */
  backlogCount: number;
  /** null = backlog 없음 */
  oldestBacklogAgeSeconds: number | null;
  /** null = 측정 불가(카운터 미등록). 값은 백엔드 프로세스 기동 이후 누적 */
  dltCount: number | null;
}

export interface OpsSeriesPoint {
  bucketStart: string;
  collectedTransactions: number;
  detectedEvents: number;
  /** null = 해당 버킷 수집 0건 */
  detectionRatePercent: number | null;
  /** true = 아직 집계가 끝나지 않은 진행 중 버킷. 구버전 백엔드는 필드 부재(프론트가 시간으로 폴백 판별) */
  partial?: boolean;
}

export interface OpsRiskStatusCell {
  riskLevel: string;
  status: string;
  count: number;
}

export interface OpsEventTypeCount {
  key: string;
  count: number;
}

export interface OpsOverview {
  generatedAt: string;
  range: string;
  bucket: string;
  collector: OpsCollector;
  kpis: OpsKpis;
  series: OpsSeriesPoint[];
  riskStatusMatrix: OpsRiskStatusCell[];
  eventTypes: OpsEventTypeCount[];
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

export interface AgentFaultStatus {
  teamId: string;
  active: boolean;
  scenario: string;
  activatedAt: string | null;
  expiresAt: string | null;
}

export interface AgentOpsSnapshot {
  overview: AgentOpsOverview;
  teams: AgentTeam[];
  handoffs: AgentHandoffEvent[];
  faults?: AgentFaultStatus[];
}
