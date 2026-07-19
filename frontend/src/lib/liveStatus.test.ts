import { describe, expect, it } from "vitest";
import { computeLiveStatus, STALE_AFTER_MS } from "./liveStatus";

const NOW = 1_752_800_000_000;

const base = {
  hasData: true,
  refreshing: false,
  resyncing: false,
  paused: false,
  allFailed: false,
  anyError: false,
  lastSuccessAt: NOW - 5_000,
  now: NOW
};

describe("computeLiveStatus", () => {
  it("최근 갱신 성공이면 LIVE", () => {
    expect(computeLiveStatus(base).kind).toBe("LIVE");
  });

  it("background refresh 중이면 UPDATING (기존 데이터 유지)", () => {
    const status = computeLiveStatus({ ...base, refreshing: true });
    expect(status.kind).toBe("UPDATING");
    expect(status.detail).toBe("기존 데이터 유지");
  });

  it("탭 복귀 동기화는 UPDATING에 별도 표기한다", () => {
    const status = computeLiveStatus({ ...base, refreshing: true, resyncing: true });
    expect(status.kind).toBe("UPDATING");
    expect(status.detail).toBe("복귀 후 동기화 중");
  });

  it("일부 위젯 실패는 STALE", () => {
    expect(computeLiveStatus({ ...base, anyError: true }).kind).toBe("STALE");
  });

  it("마지막 성공 후 기준 시간이 지나면 STALE", () => {
    expect(
      computeLiveStatus({ ...base, lastSuccessAt: NOW - STALE_AFTER_MS - 1 }).kind
    ).toBe("STALE");
    expect(
      computeLiveStatus({ ...base, lastSuccessAt: NOW - STALE_AFTER_MS + 1_000 }).kind
    ).toBe("LIVE");
  });

  it("탭 비활성화는 다른 모든 상태보다 우선해 PAUSED", () => {
    expect(computeLiveStatus({ ...base, paused: true, allFailed: true }).kind).toBe("PAUSED");
  });

  it("모든 API 실패는 OFFLINE", () => {
    expect(computeLiveStatus({ ...base, allFailed: true, anyError: true }).kind).toBe("OFFLINE");
  });

  it("OFFLINE 중 재시도(refreshing)는 UPDATING으로 보여준다", () => {
    expect(computeLiveStatus({ ...base, allFailed: true, refreshing: true }).kind).toBe("UPDATING");
  });

  it("데이터·오류 이력이 없는 초기 상태는 UPDATING", () => {
    expect(
      computeLiveStatus({ ...base, hasData: false, lastSuccessAt: null }).kind
    ).toBe("UPDATING");
  });
});
