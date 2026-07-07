import { describe, expect, it } from "vitest";
import { resolveConfirmation, resolveRuleEvidence } from "./ruleEvidence";
import { formatFullDate } from "./format";

function entryFor(entries: NonNullable<ReturnType<typeof resolveRuleEvidence>>, key: string) {
  const entry = entries.find((item) => item.key === key);
  expect(entry, `entry for key '${key}'`).toBeDefined();
  return entry!;
}

describe("resolveRuleEvidence — 방어적 처리", () => {
  it("returns null for absent/null/non-object evidence", () => {
    expect(resolveRuleEvidence(undefined)).toBeNull();
    expect(resolveRuleEvidence(null)).toBeNull();
    expect(resolveRuleEvidence("large-transfer")).toBeNull();
    expect(resolveRuleEvidence(42)).toBeNull();
    expect(resolveRuleEvidence(["rule"])).toBeNull();
    expect(resolveRuleEvidence({})).toBeNull();
  });
});

describe("resolveRuleEvidence — 룰별 알려진 키", () => {
  it("maps large-transfer evidence to Korean labels with ETH formatting", () => {
    const entries = resolveRuleEvidence({
      rule: "large-transfer",
      ruleVersion: "1.0",
      thresholdEth: 100.0,
      observedAmountEth: 250,
      fromAddress: "0xwhale",
      toAddress: "0xreceiver"
    })!;

    expect(entryFor(entries, "rule").value).toBe("대규모 이체 룰 (large-transfer)");
    expect(entryFor(entries, "thresholdEth")).toMatchObject({ label: "기준 금액", value: "100 ETH" });
    expect(entryFor(entries, "observedAmountEth")).toMatchObject({
      label: "관측 금액",
      value: "250 ETH"
    });
    expect(entryFor(entries, "fromAddress")).toMatchObject({
      label: "보낸 주소",
      value: "0xwhale",
      mono: true
    });
    expect(entries.every((entry) => entry.known)).toBe(true);
  });

  it("maps exchange-flow direction to Korean labels", () => {
    const entries = resolveRuleEvidence({
      rule: "exchange-flow",
      direction: "INBOUND",
      matchedExchangeAddress: "0xexchange",
      counterpartyAddress: "0xuser"
    })!;

    expect(entryFor(entries, "direction").value).toBe("거래소 입금 (INBOUND)");
    expect(entryFor(entries, "matchedExchangeAddress").label).toBe("매칭 거래소 주소");
    expect(entryFor(entries, "counterpartyAddress").label).toBe("상대 주소");
  });

  it("maps rapid-transfer window/count fields with units and formatted date", () => {
    const entries = resolveRuleEvidence({
      rule: "rapid-transfer",
      windowMinutes: 10,
      thresholdCount: 3,
      observedTransferCount: 5,
      windowStart: "2026-07-08T11:50:00Z"
    })!;

    expect(entryFor(entries, "windowMinutes").value).toBe("10분");
    expect(entryFor(entries, "thresholdCount").value).toBe("3회");
    expect(entryFor(entries, "observedTransferCount").value).toBe("5회");
    expect(entryFor(entries, "windowStart").value).toBe(formatFullDate("2026-07-08T11:50:00Z"));
  });

  it("maps watchlist-activity matchedDirection and keeps watchlistReason raw", () => {
    const entries = resolveRuleEvidence({
      rule: "watchlist-activity",
      matchedAddress: "0xwatched",
      matchedDirection: "FROM",
      watchlistReason: "configured-watchlist-address"
    })!;

    expect(entryFor(entries, "matchedDirection").value).toBe("watchlist 지갑이 송신 (FROM)");
    expect(entryFor(entries, "watchlistReason").value).toBe("configured-watchlist-address");
    expect(entryFor(entries, "matchedAddress").label).toBe("watchlist 매칭 주소");
  });

  it("passes through unknown direction values as-is", () => {
    const entries = resolveRuleEvidence({ direction: "SIDEWAYS" })!;
    expect(entryFor(entries, "direction").value).toBe("SIDEWAYS");
  });
});

describe("resolveRuleEvidence — 알 수 없는 키 폴백", () => {
  it("lists unknown keys with raw key/value after known keys", () => {
    const entries = resolveRuleEvidence({
      newField: "future-value",
      rule: "large-transfer",
      nested: { a: 1 }
    })!;

    const unknownEntries = entries.filter((entry) => !entry.known);
    expect(unknownEntries.map((entry) => entry.key)).toEqual(["newField", "nested"]);
    expect(entryFor(entries, "newField")).toMatchObject({
      label: "newField",
      value: "future-value",
      mono: true
    });
    expect(entryFor(entries, "nested").value).toBe('{"a":1}');
    // 알려진 키(rule)가 앞에 온다
    expect(entries[0].key).toBe("rule");
  });

  it("renders null values in unknown keys as dash", () => {
    const entries = resolveRuleEvidence({ mysteryKey: null })!;
    expect(entryFor(entries, "mysteryKey").value).toBe("-");
  });
});

describe("resolveConfirmation", () => {
  it("returns confirmed with confirmation count", () => {
    const view = resolveConfirmation({ confirmations: 18, confirmed: true });
    expect(view.kind).toBe("confirmed");
    expect(view.label).toBe("확정");
    expect(view.detail).toBe("18 confirmations");
  });

  it("returns unconfirmed for confirmed=false (distinct from unknown)", () => {
    const view = resolveConfirmation({ confirmations: 3, confirmed: false });
    expect(view.kind).toBe("unconfirmed");
    expect(view.label).toBe("미확정");
    expect(view.detail).toContain("3 confirmations");
  });

  it("returns unknown when both fields are null (head not observed)", () => {
    const view = resolveConfirmation({ confirmations: null, confirmed: null });
    expect(view.kind).toBe("unknown");
    expect(view.label).toBe("판정 불가");
  });

  it("returns unknown when fields are absent (legacy backend)", () => {
    expect(resolveConfirmation({}).kind).toBe("unknown");
  });
});
