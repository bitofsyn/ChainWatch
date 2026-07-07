import type { AiAnalysisReport, AiConfidence, AiEscalationLevel, AiStructuredAnalysis } from "../types";

/**
 * AI 리포트 렌더링 분기 (Wave1 AI 계약).
 * - none: 리포트 없음 (분석 요청 전)
 * - pending: 분석 대기/진행 중
 * - failed: 분석 실패 → 재시도 affordance 노출
 * - structured: 구조화 분석 렌더링
 * - text: 텍스트 리포트 폴백 (degraded=true면 "구조화 분석 불가" 안내)
 * - empty: 완료 상태이지만 본문 없음
 */
export type AiReportView =
  | { kind: "none" }
  | { kind: "pending" }
  | { kind: "failed" }
  | { kind: "structured"; analysis: AiStructuredAnalysis; report: string | null }
  | { kind: "text"; report: string; degraded: boolean }
  | { kind: "empty" };

export function resolveAiReportView(report: AiAnalysisReport | null | undefined): AiReportView {
  if (!report) {
    return { kind: "none" };
  }
  if (report.status === "FAILED") {
    return { kind: "failed" };
  }
  if (report.status === "PENDING" || report.status === "IN_PROGRESS") {
    return { kind: "pending" };
  }
  if (report.structured === true && report.structuredAnalysis) {
    return { kind: "structured", analysis: report.structuredAnalysis, report: report.report };
  }
  if (report.report && report.report.trim()) {
    // structured === false 명시는 v3 파이프라인이 구조화에 실패(degrade)했다는 뜻
    return { kind: "text", report: report.report, degraded: report.structured === false };
  }
  return { kind: "empty" };
}

export const CONFIDENCE_LABELS: Record<AiConfidence, string> = {
  low: "신뢰도 낮음",
  medium: "신뢰도 중간",
  high: "신뢰도 높음"
};

export const ESCALATION_LABELS: Record<AiEscalationLevel, string> = {
  none: "에스컬레이션 불필요",
  monitor: "모니터링 권고",
  escalate: "에스컬레이션 권고",
  urgent: "긴급 대응 권고"
};

export function formatConfidence(value: AiConfidence | null): string | null {
  return value ? CONFIDENCE_LABELS[value] ?? value : null;
}

export function formatEscalation(value: AiEscalationLevel | null): string | null {
  return value ? ESCALATION_LABELS[value] ?? value : null;
}
