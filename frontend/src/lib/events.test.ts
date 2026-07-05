import { describe, expect, it } from "vitest";
import { aggregateEventTypeCounts, buildEventsQuery } from "./events";
import type { DetectionEventItem } from "../types";

function event(overrides: Partial<DetectionEventItem>): DetectionEventItem {
  return {
    id: 1,
    eventType: "LARGE_TRANSFER",
    riskLevel: "HIGH",
    riskScore: 80,
    summary: "",
    walletAddress: "0xabc",
    txHash: null,
    detectedAt: "2026-07-05T00:00:00Z",
    status: "NEW" as const,
    ...overrides
  };
}

describe("buildEventsQuery", () => {
  it("includes only provided filters plus size", () => {
    const query = buildEventsQuery({ eventType: "LARGE_TRANSFER" }, 10);
    expect(query).toBe("eventType=LARGE_TRANSFER&size=10");
  });

  it("trims wallet and skips blank values", () => {
    const query = buildEventsQuery({ wallet: "  0xabc  ", riskLevel: "" }, 5);
    expect(query).toBe("wallet=0xabc&size=5");
  });

  it("returns only size when no filters set", () => {
    expect(buildEventsQuery({}, 20)).toBe("size=20");
  });
});

describe("aggregateEventTypeCounts", () => {
  it("counts events per type sorted descending", () => {
    const data = aggregateEventTypeCounts([
      event({ id: 1, eventType: "LARGE_TRANSFER" }),
      event({ id: 2, eventType: "RAPID_TRANSFER" }),
      event({ id: 3, eventType: "RAPID_TRANSFER" })
    ]);

    expect(data).toHaveLength(2);
    expect(data[0]).toMatchObject({ key: "RAPID_TRANSFER", count: 2, label: "반복 이체" });
    expect(data[1]).toMatchObject({ key: "LARGE_TRANSFER", count: 1 });
  });

  it("returns empty array for no events", () => {
    expect(aggregateEventTypeCounts([])).toEqual([]);
  });
});
