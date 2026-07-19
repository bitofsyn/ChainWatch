import { describe, expect, it } from "vitest";
import type { OpsOverview } from "../types";
import {
  changeTone,
  diffOverview,
  diffValue,
  findNewKeys,
  newBucketKeys
} from "./overviewDiff";

const NOW = 1_752_800_000_000;

function overview(partial: {
  lagBlocks?: number | null;
  tpm?: number;
  rate?: number | null;
  backlog?: number;
  series?: OpsOverview["series"];
}): OpsOverview {
  return {
    generatedAt: new Date(NOW).toISOString(),
    range: "24h",
    bucket: "1h",
    collector: {
      chainHead: 100,
      lastCollectedBlock: 100,
      lagBlocks: partial.lagBlocks ?? 0,
      confirmationDepth: 12,
      status: "UP"
    },
    kpis: {
      transactionsPerMinute: partial.tpm ?? 0,
      transactionsDeltaPercent: null,
      detectionRatePercent: partial.rate ?? null,
      detectedLast5m: 0,
      backlogCount: partial.backlog ?? 0,
      oldestBacklogAgeSeconds: null,
      dltCount: null
    },
    series: partial.series ?? [],
    riskStatusMatrix: [],
    eventTypes: []
  };
}

describe("diffValue", () => {
  it("최초 로드(previous 부재)는 변화로 취급하지 않는다", () => {
    const change = diffValue(undefined, 42, NOW);
    expect(change.direction).toBe("initial");
    expect(change.delta).toBeNull();
    expect(change.deltaPercent).toBeNull();
  });

  it("상승/하락/동일을 판정하고 delta를 계산한다", () => {
    expect(diffValue(10, 15, NOW)).toMatchObject({
      direction: "up",
      delta: 5,
      deltaPercent: 50
    });
    expect(diffValue(10, 6, NOW)).toMatchObject({ direction: "down", delta: -4 });
    expect(diffValue(10, 10, NOW)).toMatchObject({ direction: "same", delta: 0 });
  });

  it("null 전환은 delta를 만들지 않는다 (null과 0 구분)", () => {
    // null → 값: 비교 불가, initial
    expect(diffValue(null, 5, NOW).direction).toBe("initial");
    // 값 → null: 미측정 전환, 애니메이션 금지(same)
    const gone = diffValue(5, null, NOW);
    expect(gone.direction).toBe("same");
    expect(gone.delta).toBeNull();
    // 0은 측정된 값이므로 정상 비교
    expect(diffValue(0, 3, NOW).direction).toBe("up");
    expect(diffValue(0, 3, NOW).deltaPercent).toBeNull(); // 분모 0
  });
});

describe("changeTone", () => {
  it("실제 변화(up/down)에만 tone을 부여한다", () => {
    expect(changeTone(diffValue(undefined, 5, NOW), "higher-worse")).toBe("neutral");
    expect(changeTone(diffValue(5, 5, NOW), "higher-worse")).toBe("neutral");
  });

  it("higher-worse KPI는 상승이 주의, 하락이 개선이다", () => {
    expect(changeTone(diffValue(5, 9, NOW), "higher-worse")).toBe("caution");
    expect(changeTone(diffValue(9, 5, NOW), "higher-worse")).toBe("improve");
  });

  it("neutral KPI(처리량)는 방향과 무관하게 중립이다", () => {
    expect(changeTone(diffValue(5, 9, NOW), "neutral")).toBe("neutral");
  });
});

describe("diffOverview", () => {
  it("previous가 null이면(최초 로드·query transition) 전부 initial이다", () => {
    const changes = diffOverview(null, overview({ lagBlocks: 3, tpm: 12 }), NOW);
    expect(changes.lagBlocks.direction).toBe("initial");
    expect(changes.transactionsPerMinute.direction).toBe("initial");
    expect(changes.detectionRatePercent.direction).toBe("initial");
    expect(changes.backlogCount.direction).toBe("initial");
  });

  it("KPI별로 방향을 독립 판정한다", () => {
    const prev = overview({ lagBlocks: 10, tpm: 5, rate: 2, backlog: 4 });
    const next = overview({ lagBlocks: 2, tpm: 8, rate: 2, backlog: 9 });
    const changes = diffOverview(prev, next, NOW);
    expect(changes.lagBlocks.direction).toBe("down");
    expect(changes.transactionsPerMinute.direction).toBe("up");
    expect(changes.detectionRatePercent.direction).toBe("same");
    expect(changes.backlogCount).toMatchObject({ direction: "up", delta: 5 });
  });
});

describe("findNewKeys", () => {
  it("이전 응답에 없던 key만 신규다", () => {
    const fresh = findNewKeys(new Set(["1", "2"]), ["2", "3", "4"]);
    expect([...fresh].sort()).toEqual(["3", "4"]);
  });

  it("최초 로드(previous null)는 아무것도 신규로 표시하지 않는다", () => {
    expect(findNewKeys(null, ["1", "2"]).size).toBe(0);
  });
});

describe("newBucketKeys", () => {
  const point = (iso: string) => ({
    bucketStart: iso,
    collectedTransactions: 1,
    detectedEvents: 0,
    detectionRatePercent: null
  });

  it("오른쪽에 추가된 bucket만 골라낸다", () => {
    const prev = [point("2026-07-18T01:00:00Z"), point("2026-07-18T02:00:00Z")];
    const next = [...prev, point("2026-07-18T03:00:00Z")];
    expect([...newBucketKeys(prev, next)]).toEqual(["2026-07-18T03:00:00Z"]);
  });

  it("최초 로드는 빈 집합이다", () => {
    expect(newBucketKeys(null, [point("2026-07-18T01:00:00Z")]).size).toBe(0);
  });
});
