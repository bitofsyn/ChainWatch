import { describe, expect, it } from "vitest";
import { formatEventType, shortenAddress, toStatus } from "./format";

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

describe("shortenAddress", () => {
  it("shortens long addresses with ellipsis", () => {
    expect(shortenAddress("0x1234567890abcdef1234567890abcdef12345678"))
      .toBe("0x123456...345678");
  });

  it("keeps short values unchanged", () => {
    expect(shortenAddress("0xabc")).toBe("0xabc");
  });
});
