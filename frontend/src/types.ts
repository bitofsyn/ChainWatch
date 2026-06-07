export type EventStatus = "critical" | "high" | "elevated";

export interface DetectionEventItem {
  id: number;
  eventType: string;
  riskLevel: string;
  riskScore: number;
  summary: string;
  walletAddress: string;
  txHash: string | null;
  detectedAt: string;
}

export interface DetectionEventPage {
  content: DetectionEventItem[];
  totalElements: number;
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
  transactionId: number;
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
