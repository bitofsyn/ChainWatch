import { describe, expect, it } from "vitest";
import { formatConfidence, formatEscalation, resolveAiReportView } from "./aiReport";
import type { AiAnalysisReport, AiStructuredAnalysis } from "../types";

const structuredAnalysis: AiStructuredAnalysis = {
  riskSummary: "고위험 대량 이체 정황이 탐지되었습니다.",
  evidence: [{ source: "riskScore", fact: "위험 점수 90 (HIGH 등급)" }],
  possibleScenarios: ["자금 세탁 목적의 분산 이동"],
  recommendedActions: ["지갑 주소 워치리스트 등재"],
  confidence: "medium",
  falsePositiveFactors: ["거래소 내부 이동 가능성"],
  escalationLevel: "escalate"
};

function report(overrides: Partial<AiAnalysisReport> = {}): AiAnalysisReport {
  return {
    id: 10,
    status: "COMPLETED",
    provider: "claude",
    model: "claude-opus-4-8",
    promptSummary: "고액 이체 탐지",
    report: "## 개요\n리포트 본문",
    analyzedAt: "2026-07-08T00:00:00Z",
    ...overrides
  };
}

describe("resolveAiReportView", () => {
  it("returns none when there is no report", () => {
    expect(resolveAiReportView(null)).toEqual({ kind: "none" });
    expect(resolveAiReportView(undefined)).toEqual({ kind: "none" });
  });

  it("returns failed for FAILED status (retry affordance)", () => {
    expect(resolveAiReportView(report({ status: "FAILED" }))).toEqual({ kind: "failed" });
  });

  it("returns pending for PENDING and IN_PROGRESS", () => {
    expect(resolveAiReportView(report({ status: "PENDING" })).kind).toBe("pending");
    expect(resolveAiReportView(report({ status: "IN_PROGRESS" })).kind).toBe("pending");
  });

  it("returns structured view when structured=true with analysis", () => {
    const view = resolveAiReportView(
      report({ structured: true, structuredAnalysis: structuredAnalysis })
    );
    expect(view.kind).toBe("structured");
    if (view.kind === "structured") {
      expect(view.analysis.confidence).toBe("medium");
      expect(view.analysis.escalationLevel).toBe("escalate");
    }
  });

  it("falls back to degraded text when structured=false", () => {
    const view = resolveAiReportView(report({ structured: false, structuredAnalysis: null }));
    expect(view).toEqual({ kind: "text", report: "## 개요\n리포트 본문", degraded: true });
  });

  it("treats legacy reports (no structured flag) as plain text without degraded notice", () => {
    const view = resolveAiReportView(report());
    expect(view).toEqual({ kind: "text", report: "## 개요\n리포트 본문", degraded: false });
  });

  it("does not trust structured flag without the analysis payload", () => {
    const view = resolveAiReportView(report({ structured: true, structuredAnalysis: null }));
    expect(view.kind).toBe("text");
  });

  it("returns empty for completed report without body", () => {
    expect(resolveAiReportView(report({ report: null })).kind).toBe("empty");
    expect(resolveAiReportView(report({ report: "   " })).kind).toBe("empty");
  });
});

describe("confidence/escalation labels", () => {
  it("maps known values to Korean labels", () => {
    expect(formatConfidence("high")).toBe("신뢰도 높음");
    expect(formatEscalation("urgent")).toBe("긴급 대응 권고");
  });

  it("returns null for null values", () => {
    expect(formatConfidence(null)).toBeNull();
    expect(formatEscalation(null)).toBeNull();
  });
});
