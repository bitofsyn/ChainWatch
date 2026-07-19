import { describe, expect, it } from "vitest";
import type { OpsSeriesPoint } from "../types";
import {
  clampTooltipPercent,
  computeScales,
  gappedLinePath,
  interpolateSeries,
  isPartialBucket,
  linePath,
  nearestBucketIndex,
  parseBucketMs,
  sharesBuckets
} from "./chartGeometry";

const FRAME = { width: 720, height: 240, padLeft: 46, padRight: 44, padTop: 14, padBottom: 28 };

function point(
  iso: string,
  collected: number,
  detected = 0,
  rate: number | null = null
): OpsSeriesPoint {
  return {
    bucketStart: iso,
    collectedTransactions: collected,
    detectedEvents: detected,
    detectionRatePercent: rate
  };
}

describe("computeScales", () => {
  const series = [point("a", 100, 20, 20), point("b", 50, 10, 5)];

  it("0 baseline을 유지하고 최대값을 데이터에 맞춘다", () => {
    const scales = computeScales(series, FRAME);
    expect(scales.maxCount).toBe(100);
    expect(scales.yCount(0)).toBe(FRAME.height - FRAME.padBottom);
    expect(scales.yCount(100)).toBe(FRAME.padTop);
  });

  it("빈 시리즈에서도 0으로 나누지 않는다", () => {
    const scales = computeScales([], FRAME);
    expect(scales.maxCount).toBe(1);
    expect(Number.isFinite(scales.slot)).toBe(true);
  });

  it("탐지율 축은 최소 10%를 보장해 작은 값이 과장되지 않는다", () => {
    expect(computeScales([point("a", 1, 0, 2)], FRAME).maxRate).toBe(10);
  });
});

describe("nearestBucketIndex", () => {
  it("slot 중앙 기준으로 가장 가까운 bucket을 찾는다", () => {
    // innerWidth 630, 6 buckets → slot 105
    expect(nearestBucketIndex(46 + 10, 6, FRAME)).toBe(0);
    expect(nearestBucketIndex(46 + 105 * 2 + 50, 6, FRAME)).toBe(2);
    expect(nearestBucketIndex(46 + 105 * 6 - 1, 6, FRAME)).toBe(5);
  });

  it("plot 바깥은 null, 경계 근처는 클램프한다", () => {
    expect(nearestBucketIndex(-500, 6, FRAME)).toBeNull();
    expect(nearestBucketIndex(5_000, 6, FRAME)).toBeNull();
    expect(nearestBucketIndex(44, 6, FRAME)).toBe(0);
    expect(nearestBucketIndex(100, 0, FRAME)).toBeNull();
  });
});

describe("parseBucketMs / isPartialBucket", () => {
  it("버킷 문자열을 ms로 해석한다", () => {
    expect(parseBucketMs("5m")).toBe(300_000);
    expect(parseBucketMs("15m")).toBe(900_000);
    expect(parseBucketMs("1h")).toBe(3_600_000);
    expect(parseBucketMs("nope")).toBeNull();
  });

  it("bucketStart + 길이 > now 이면 집계 중(partial)이다", () => {
    const now = Date.parse("2026-07-18T10:30:00Z");
    expect(isPartialBucket("2026-07-18T10:00:00Z", 3_600_000, now)).toBe(true);
    expect(isPartialBucket("2026-07-18T09:00:00Z", 3_600_000, now)).toBe(false);
    // bucket 길이를 모르면 판정하지 않는다(추측 금지)
    expect(isPartialBucket("2026-07-18T10:00:00Z", null, now)).toBe(false);
  });
});

describe("path 생성", () => {
  it("연속 좌표를 하나의 path로 만든다", () => {
    expect(linePath([{ x: 1, y: 2 }, { x: 3, y: 4 }])).toBe("M1.0,2.0 L3.0,4.0");
  });

  it("null 값은 선으로 잇지 않고 gap으로 남긴다", () => {
    const path = gappedLinePath([
      { x: 1, y: 1 },
      { x: 2, y: 2 },
      null,
      { x: 4, y: 4 },
      { x: 5, y: 5 }
    ]);
    expect(path).toBe("M1.0,1.0 L2.0,2.0 M4.0,4.0 L5.0,5.0");
  });
});

describe("interpolateSeries", () => {
  const prev = [point("a", 100, 10, 10), point("b", 200, 20, 10)];

  it("같은 bucket key끼리 값을 보간한다", () => {
    const next = [point("a", 200, 30, 20), point("b", 100, 0, 0)];
    const mid = interpolateSeries(prev, next, 0.5);
    expect(mid[0].collectedTransactions).toBe(150);
    expect(mid[0].detectedEvents).toBe(20);
    expect(mid[0].detectionRatePercent).toBe(15);
    expect(mid[1].collectedTransactions).toBe(150);
  });

  it("이전에 없던 bucket은 보간 없이 최종값으로 스냅한다", () => {
    const next = [point("b", 100), point("c", 500)];
    const mid = interpolateSeries(prev, next, 0.1);
    expect(mid.find((p) => p.bucketStart === "c")?.collectedTransactions).toBe(500);
  });

  it("null 탐지율은 보간하지 않는다 (null과 0 구분)", () => {
    const next = [point("a", 100, 10, null)];
    expect(interpolateSeries(prev, next, 0.5)[0].detectionRatePercent).toBeNull();
    const fromNull = interpolateSeries([point("a", 1, 0, null)], [point("a", 1, 0, 8)], 0.5);
    expect(fromNull[0].detectionRatePercent).toBe(8);
  });

  it("t>=1이면 최종 시리즈를 그대로 반환한다", () => {
    const next = [point("a", 1)];
    expect(interpolateSeries(prev, next, 1)).toBe(next);
  });
});

describe("sharesBuckets", () => {
  it("bucket 집합이 겹치면 morph, 완전히 다르면 query transition 대상이다", () => {
    expect(sharesBuckets([point("a", 1)], [point("a", 2), point("b", 1)])).toBe(true);
    expect(sharesBuckets([point("a", 1)], [point("x", 1)])).toBe(false);
  });
});

describe("clampTooltipPercent", () => {
  it("가장자리 bucket에서 tooltip이 잘리지 않게 클램프한다", () => {
    expect(clampTooltipPercent(0, 24)).toBe(10);
    expect(clampTooltipPercent(23, 24)).toBe(90);
    expect(clampTooltipPercent(11, 24)).toBeCloseTo((11.5 / 24) * 100);
    expect(clampTooltipPercent(0, 0)).toBe(50);
  });
});
