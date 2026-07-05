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
