import { describe, expect, it } from "vitest";
import type { DetectionEventItem, OpsSeriesPoint, PipelineComponent } from "../types";
import {
  backlogLevel,
  collectorLevel,
  formatAge,
  formatCompact,
  formatDelta,
  formatPercent,
  lagLevel,
  overallStatus,
  sortQueue,
  throughputInsight
} from "./opsOverview";

function point(collected: number, detected: number, index: number): OpsSeriesPoint {
  return {
    bucketStart: new Date(Date.UTC(2026, 6, 17, index)).toISOString(),
    collectedTransactions: collected,
    detectedEvents: detected,
    detectionRatePercent: collected === 0 ? null : (detected / collected) * 100
  };
}

describe("lagLevel", () => {
  it("임계값 경계를 정확히 판정한다", () => {
    expect(lagLevel(null)).toBe("unknown");
    expect(lagLevel(0)).toBe("ok");
    expect(lagLevel(30)).toBe("ok");
    expect(lagLevel(31)).toBe("warn");
    expect(lagLevel(300)).toBe("warn");
    expect(lagLevel(301)).toBe("danger");
  });
});

describe("backlogLevel", () => {
  it("건수와 최장 대기 시간 중 더 나쁜 쪽을 따른다", () => {
    expect(backlogLevel(0, null)).toBe("ok");
    expect(backlogLevel(21, 60)).toBe("warn");
    expect(backlogLevel(5, 31 * 60)).toBe("warn");
    expect(backlogLevel(101, 60)).toBe("danger");
    expect(backlogLevel(5, 3 * 60 * 60)).toBe("danger");
  });
});

describe("collectorLevel", () => {
  it("백엔드 상태를 KPI 레벨로 변환한다", () => {
    expect(collectorLevel("UP")).toBe("ok");
    expect(collectorLevel("DEGRADED")).toBe("warn");
    expect(collectorLevel("DOWN")).toBe("danger");
    expect(collectorLevel("UNKNOWN")).toBe("unknown");
  });
});

describe("overallStatus", () => {
  const up = (name: string): PipelineComponent => ({ name, status: "UP", detail: "" });
  const down = (name: string): PipelineComponent => ({ name, status: "DOWN", detail: "" });
  const collector = (status: "UP" | "DEGRADED" | "DOWN" | "UNKNOWN") => ({
    chainHead: 100,
    lastCollectedBlock: 100,
    lagBlocks: 0,
    confirmationDepth: 12,
    status
  });
  const kpis = (backlogCount = 0, dltCount: number | null = null) => ({
    transactionsPerMinute: 0,
    transactionsDeltaPercent: null,
    detectionRatePercent: null,
    detectedLast5m: 0,
    backlogCount,
    oldestBacklogAgeSeconds: null,
    dltCount
  });

  it("아무 데이터도 없으면 확인 불가", () => {
    expect(overallStatus(null, null, null).label).toBe("확인 불가");
  });

  it("핵심 컴포넌트 DOWN이면 장애", () => {
    expect(overallStatus([down("kafka")], collector("UP"), kpis()).label).toBe("장애");
  });

  it("비핵심 컴포넌트 DOWN이면 주의", () => {
    expect(overallStatus([up("kafka"), down("aiServer")], collector("UP"), kpis()).label).toBe("주의");
  });

  it("DLT 격리가 있으면 주의", () => {
    expect(overallStatus([up("kafka")], collector("UP"), kpis(0, 3)).label).toBe("주의");
  });

  it("모두 정상이면 정상", () => {
    expect(overallStatus([up("kafka"), up("database")], collector("UP"), kpis()).label).toBe("정상");
  });
});

describe("throughputInsight", () => {
  it("수집·탐지 동반 급증은 catch-up으로 판정한다", () => {
    const series = [
      point(10, 1, 0),
      point(12, 1, 1),
      point(11, 2, 2),
      point(400, 60, 3), // peak
      point(10, 1, 4),
      point(0, 0, 5) // 진행 중 버킷(제외)
    ];
    const insight = throughputInsight(series);
    expect(insight?.kind).toBe("catch-up");
    expect(insight?.bucketStart).toBe(series[3].bucketStart);
  });

  it("탐지만 급증하면 rule-anomaly로 판정한다", () => {
    const series = [
      point(100, 2, 0),
      point(110, 3, 1),
      point(105, 2, 2),
      point(108, 90, 3), // 탐지만 급증
      point(102, 2, 4),
      point(0, 0, 5)
    ];
    expect(throughputInsight(series)?.kind).toBe("rule-anomaly");
  });

  it("급증이 없으면 null", () => {
    const series = [point(10, 1, 0), point(11, 2, 1), point(12, 1, 2), point(10, 2, 3), point(9, 1, 4)];
    expect(throughputInsight(series)).toBeNull();
  });

  it("버킷이 4개 미만이면 판정하지 않는다", () => {
    expect(throughputInsight([point(10, 1, 0), point(500, 90, 1), point(0, 0, 2)])).toBeNull();
  });
});

describe("sortQueue", () => {
  function event(id: number, riskLevel: string, riskScore: number, detectedAt: string): DetectionEventItem {
    return {
      id,
      eventType: "LARGE_TRANSFER",
      riskLevel,
      riskScore,
      summary: "",
      walletAddress: "0x1",
      txHash: null,
      network: "ethereum-mainnet",
      detectedAt,
      status: "NEW"
    };
  }

  it("CRITICAL 우선 → 점수 내림차순 → 오래 대기한 순", () => {
    const sorted = sortQueue([
      event(1, "HIGH", 99, "2026-07-17T01:00:00Z"),
      event(2, "CRITICAL", 90, "2026-07-17T02:00:00Z"),
      event(3, "CRITICAL", 95, "2026-07-17T03:00:00Z"),
      event(4, "CRITICAL", 95, "2026-07-17T01:00:00Z")
    ]);
    expect(sorted.map((item) => item.id)).toEqual([4, 3, 2, 1]);
  });
});

describe("포맷터", () => {
  it("null은 항상 — 로 표시한다", () => {
    expect(formatCompact(null)).toBe("—");
    expect(formatPercent(null)).toBe("—");
    expect(formatDelta(null)).toBe("—");
    expect(formatAge(null)).toBe("—");
  });

  it("증감률에 부호를 명시한다", () => {
    expect(formatDelta(12.34)).toBe("+12.3%");
    expect(formatDelta(-8.21)).toBe("−8.2%");
    expect(formatDelta(0)).toBe("±0.0%");
  });

  it("age를 사람이 읽는 단위로 줄인다", () => {
    expect(formatAge(45)).toBe("45초");
    expect(formatAge(180)).toBe("3분");
    expect(formatAge(3660)).toBe("1시간 1분");
    expect(formatAge(7200)).toBe("2시간");
    expect(formatAge(90000)).toBe("1일 1시간");
  });

  it("ko-KR 축약 표기를 사용한다", () => {
    expect(formatCompact(25000)).toBe("2.5만");
    expect(formatCompact(320)).toBe("320");
  });
});
