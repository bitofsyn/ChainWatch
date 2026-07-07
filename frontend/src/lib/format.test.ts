import { describe, expect, it } from "vitest";
import {
  formatAmount,
  formatEventType,
  formatLifecycleStatus,
  LIFECYCLE_STATUS_ORDER,
  shortenAddress,
  toStatus
} from "./format";

describe("toStatus", () => {
  it("maps risk score to status buckets", () => {
    expect(toStatus(85)).toBe("critical");
    expect(toStatus(84)).toBe("high");
    expect(toStatus(70)).toBe("high");
    expect(toStatus(69)).toBe("elevated");
  });
});

describe("formatEventType", () => {
  it("translates known types and passes through unknown types", () => {
    expect(formatEventType("LARGE_TRANSFER")).toBe("대규모 이체");
    expect(formatEventType("UNKNOWN_TYPE")).toBe("UNKNOWN_TYPE");
  });
});

describe("formatLifecycleStatus", () => {
  it("includes FALSE_POSITIVE in labels and order", () => {
    expect(formatLifecycleStatus("FALSE_POSITIVE")).toBe("오탐 종결");
    expect(LIFECYCLE_STATUS_ORDER).toContain("FALSE_POSITIVE");
  });

  it("passes through unknown statuses", () => {
    expect(formatLifecycleStatus("SOMETHING_ELSE")).toBe("SOMETHING_ELSE");
  });
});

describe("formatAmount", () => {
  it("formats amounts with unit and thousand separators", () => {
    expect(formatAmount(1234.5)).toBe("1,234.5 ETH");
    expect(formatAmount(0.000001)).toBe("0.000001 ETH");
    expect(formatAmount(10, "BTC")).toBe("10 BTC");
  });
});

describe("shortenAddress", () => {
  it("shortens long addresses with ellipsis", () => {
    expect(shortenAddress("0x1234567890abcdef1234567890abcdef12345678"))
      .toBe("0x123456...345678");
  });

  it("keeps short values unchanged", () => {
    expect(shortenAddress("0xabc")).toBe("0xabc");
  });
});
