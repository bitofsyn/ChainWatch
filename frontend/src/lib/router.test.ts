import { describe, expect, it } from "vitest";
import {
  matchAdminSection,
  matchAgentTeamDetail,
  matchEventDetail,
  matchOverview,
  matchTransactionDetail,
  matchWalletDetail,
  overviewPath,
  parseOverviewRange
} from "./router";

describe("Overview range URL 보존", () => {
  it("Overview 라우트와 쿼리를 매칭한다", () => {
    expect(matchOverview("/")).toBe("");
    expect(matchOverview("/?range=6h")).toBe("range=6h");
    expect(matchOverview("/events")).toBeNull();
  });

  it("URL의 range를 파싱하고 알 수 없는 값은 null", () => {
    expect(parseOverviewRange("/?range=1h")).toBe("1h");
    expect(parseOverviewRange("/?range=6h")).toBe("6h");
    expect(parseOverviewRange("/")).toBeNull();
    expect(parseOverviewRange("/?range=99h")).toBeNull();
  });

  it("기본값 24h는 쿼리 없는 경로로 유지한다", () => {
    expect(overviewPath("24h")).toBe("/");
    expect(overviewPath("1h")).toBe("/?range=1h");
  });
});

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
