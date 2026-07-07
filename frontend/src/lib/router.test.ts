import { describe, expect, it } from "vitest";
import {
  matchAdminSection,
  matchAgentTeamDetail,
  matchEventDetail,
  matchTransactionDetail,
  matchWalletDetail
} from "./router";

describe("matchEventDetail", () => {
  it("matches numeric event detail routes", () => {
    expect(matchEventDetail("/events/42")).toBe(42);
    expect(matchEventDetail("/events/abc")).toBeNull();
    expect(matchEventDetail("/events")).toBeNull();
  });
});

describe("matchTransactionDetail", () => {
  it("matches numeric transaction routes", () => {
    expect(matchTransactionDetail("/transactions/7")).toBe(7);
    expect(matchTransactionDetail("/transactions/7/extra")).toBeNull();
  });
});

describe("matchWalletDetail", () => {
  it("decodes wallet address segment", () => {
    expect(matchWalletDetail("/wallets/0xabc")).toBe("0xabc");
    expect(matchWalletDetail(`/wallets/${encodeURIComponent("0xAB/cd")}`)).toBe("0xAB/cd");
    expect(matchWalletDetail("/wallets")).toBeNull();
  });
});

describe("matchAgentTeamDetail", () => {
  it("matches team detail routes", () => {
    expect(matchAgentTeamDetail("/agents/teams/detection")).toBe("detection");
    expect(matchAgentTeamDetail("/agents/teams")).toBeNull();
  });
});

describe("matchAdminSection", () => {
  it("maps /admin to dashboard", () => {
    expect(matchAdminSection("/admin")).toBe("dashboard");
  });

  it("matches known sections including audit", () => {
    expect(matchAdminSection("/admin/pipeline")).toBe("pipeline");
    expect(matchAdminSection("/admin/analysis")).toBe("analysis");
    expect(matchAdminSection("/admin/policies")).toBe("policies");
    expect(matchAdminSection("/admin/audit")).toBe("audit");
  });

  it("rejects unknown sections", () => {
    expect(matchAdminSection("/admin/unknown")).toBeNull();
  });
});
