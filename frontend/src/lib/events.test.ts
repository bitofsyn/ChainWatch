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
    network: "ethereum-mainnet",
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

  it("includes status filter including FALSE_POSITIVE", () => {
    expect(buildEventsQuery({ status: "FALSE_POSITIVE" }, 20)).toBe("status=FALSE_POSITIVE&size=20");
    expect(buildEventsQuery({ status: "NEW" }, 20)).toBe("status=NEW&size=20");
  });

  it("skips empty status filter", () => {
    expect(buildEventsQuery({ status: "" }, 20)).toBe("size=20");
  });

  it("includes network filter", () => {
    expect(buildEventsQuery({ network: "polygon-mainnet" }, 20)).toBe("network=polygon-mainnet&size=20");
  });

  it("unassigned takes precedence over assignee", () => {
    expect(buildEventsQuery({ unassigned: true, assignee: "alice" }, 20)).toBe("unassigned=true&size=20");
    expect(buildEventsQuery({ assignee: "alice" }, 20)).toBe("assignee=alice&size=20");
  });

  it("converts from/to datetime-local values to ISO instants", () => {
    const query = buildEventsQuery({ from: "2026-07-01T09:00", to: "2026-07-02T09:00" }, 20);
    const params = new URLSearchParams(query);
    expect(params.get("from")).toBe(new Date("2026-07-01T09:00").toISOString());
    expect(params.get("to")).toBe(new Date("2026-07-02T09:00").toISOString());
  });

  it("skips invalid from/to values", () => {
    expect(buildEventsQuery({ from: "not-a-date" }, 20)).toBe("size=20");
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
