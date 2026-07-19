import type { OpsSeriesPoint } from "../types";

/**
 * TimeSeriesChart의 좌표·판정 로직. 렌더링과 분리한 순수 함수 모음.
 * - null(미측정)과 0(측정된 0건)을 구분한다: 탐지율 null 버킷은 선을 잇지 않고 gap으로 남긴다.
 * - partial bucket은 API에 완료 플래그가 없어 bucketStart + bucket 길이 > now 로
 *   결정론적으로 판별한다(서버 generatedAt 기준 권장). 이 가정은 문서화된 계약이다.
 */

export interface ChartFrame {
  width: number;
  height: number;
  padLeft: number;
  padRight: number;
  padTop: number;
  padBottom: number;
}

export interface ChartScales {
  innerWidth: number;
  innerHeight: number;
  maxCount: number;
  maxRate: number;
  slot: number;
  x: (index: number) => number;
  yCount: (value: number) => number;
  yRate: (value: number) => number;
}

/** 0 baseline 고정. 자동 범위로 작은 변동이 과장되지 않게 max만 데이터에 맞춘다. */
export function computeScales(series: OpsSeriesPoint[], frame: ChartFrame): ChartScales {
  const innerWidth = frame.width - frame.padLeft - frame.padRight;
  const innerHeight = frame.height - frame.padTop - frame.padBottom;
  const maxCount = Math.max(
    1,
    ...series.map((point) => Math.max(point.collectedTransactions, point.detectedEvents))
  );
  const maxRate = Math.max(10, ...series.map((point) => point.detectionRatePercent ?? 0));
  const slot = series.length > 0 ? innerWidth / series.length : innerWidth;
  return {
    innerWidth,
    innerHeight,
    maxCount,
    maxRate,
    slot,
    x: (index) => frame.padLeft + slot * index,
    yCount: (value) => frame.padTop + innerHeight * (1 - value / maxCount),
    yRate: (value) => frame.padTop + innerHeight * (1 - value / maxRate)
  };
}

/**
 * pointer의 SVG x좌표에서 가장 가까운 bucket index.
 * plot 영역 밖(패딩 바깥)이면 null. 경계는 slot 중앙 기준으로 반올림한다.
 */
export function nearestBucketIndex(
  svgX: number,
  seriesLength: number,
  frame: Pick<ChartFrame, "padLeft" | "padRight" | "width">
): number | null {
  if (seriesLength === 0) {
    return null;
  }
  const innerWidth = frame.width - frame.padLeft - frame.padRight;
  const slot = innerWidth / seriesLength;
  if (svgX < frame.padLeft - slot / 2 || svgX > frame.width - frame.padRight + slot / 2) {
    return null;
  }
  const index = Math.floor((svgX - frame.padLeft) / slot);
  return Math.min(seriesLength - 1, Math.max(0, index));
}

/** "5m" | "15m" | "1h" 형식의 bucket 길이를 ms로. 알 수 없으면 null. */
export function parseBucketMs(bucket: string): number | null {
  const match = bucket.match(/^(\d+)([mh])$/);
  if (!match) {
    return null;
  }
  const amount = Number(match[1]);
  return match[2] === "m" ? amount * 60_000 : amount * 3_600_000;
}

/** 아직 집계가 끝나지 않은 진행 중 버킷인지. bucket 길이를 모르면 판정하지 않는다(false). */
export function isPartialBucket(bucketStart: string, bucketMs: number | null, now: number): boolean {
  if (bucketMs == null) {
    return false;
  }
  const start = new Date(bucketStart).getTime();
  return Number.isFinite(start) && start + bucketMs > now;
}

/**
 * partial 판정: 서버 계약(point.partial)을 우선하고,
 * 필드가 없는 구버전 응답만 bucketStart + bucket 길이 > now 로 폴백 판별한다.
 */
export function bucketPartial(
  point: Pick<OpsSeriesPoint, "bucketStart" | "partial">,
  bucketMs: number | null,
  now: number
): boolean {
  return point.partial ?? isPartialBucket(point.bucketStart, bucketMs, now);
}

export interface LinePoint {
  x: number;
  y: number;
}

/** 연속 좌표를 하나의 path로. */
export function linePath(points: readonly LinePoint[]): string {
  return points
    .map((point, index) => `${index === 0 ? "M" : "L"}${point.x.toFixed(1)},${point.y.toFixed(1)}`)
    .join(" ");
}

/**
 * Fritsch–Carlson monotone cubic 보간 path.
 * 데이터에 없는 봉우리/골을 만들지 않아(overshoot 없음) 관제 지표를 왜곡하지 않으면서
 * 직선 연결보다 시각적으로 부드럽다. x 오름차순 좌표를 전제한다.
 */
export function monotonePath(points: readonly LinePoint[]): string {
  if (points.length === 0) {
    return "";
  }
  if (points.length === 1) {
    return `M${points[0].x.toFixed(1)},${points[0].y.toFixed(1)}`;
  }
  const n = points.length;
  const dx: number[] = [];
  const slope: number[] = [];
  for (let i = 0; i < n - 1; i++) {
    const h = points[i + 1].x - points[i].x;
    dx.push(h);
    slope.push(h === 0 ? 0 : (points[i + 1].y - points[i].y) / h);
  }
  const tangent: number[] = new Array(n);
  tangent[0] = slope[0];
  tangent[n - 1] = slope[n - 2];
  for (let i = 1; i < n - 1; i++) {
    tangent[i] = slope[i - 1] * slope[i] <= 0 ? 0 : (slope[i - 1] + slope[i]) / 2;
  }
  // 단조성 보존: 구간 기울기 대비 접선을 제한해 overshoot을 막는다.
  for (let i = 0; i < n - 1; i++) {
    if (slope[i] === 0) {
      tangent[i] = 0;
      tangent[i + 1] = 0;
      continue;
    }
    const a = tangent[i] / slope[i];
    const b = tangent[i + 1] / slope[i];
    const h = Math.hypot(a, b);
    if (h > 3) {
      const scale = 3 / h;
      tangent[i] = scale * a * slope[i];
      tangent[i + 1] = scale * b * slope[i];
    }
  }
  const parts = [`M${points[0].x.toFixed(1)},${points[0].y.toFixed(1)}`];
  for (let i = 0; i < n - 1; i++) {
    const third = dx[i] / 3;
    const c1x = points[i].x + third;
    const c1y = points[i].y + tangent[i] * third;
    const c2x = points[i + 1].x - third;
    const c2y = points[i + 1].y - tangent[i + 1] * third;
    parts.push(
      `C${c1x.toFixed(1)},${c1y.toFixed(1)} ${c2x.toFixed(1)},${c2y.toFixed(1)} ` +
        `${points[i + 1].x.toFixed(1)},${points[i + 1].y.toFixed(1)}`
    );
  }
  return parts.join(" ");
}

/** monotonePath의 gap 지원판: null 구간마다 세그먼트를 끊는다. */
export function gappedMonotonePath(points: readonly (LinePoint | null)[]): string {
  const segments: string[] = [];
  let run: LinePoint[] = [];
  const flush = () => {
    if (run.length > 0) {
      segments.push(monotonePath(run));
      run = [];
    }
  };
  for (const point of points) {
    if (point == null) {
      flush();
    } else {
      run.push(point);
    }
  }
  flush();
  return segments.join(" ");
}

/**
 * null 값을 gap으로 남기는 path. null 구간마다 새 M 세그먼트를 시작해
 * 미측정 구간을 선으로 잇지 않는다.
 */
export function gappedLinePath(points: readonly (LinePoint | null)[]): string {
  const segments: string[] = [];
  let pen = false;
  for (const point of points) {
    if (point == null) {
      pen = false;
      continue;
    }
    segments.push(`${pen ? "L" : "M"}${point.x.toFixed(1)},${point.y.toFixed(1)}`);
    pen = true;
  }
  return segments.join(" ");
}

/**
 * polling 갱신 morph용 시리즈 보간. bucketStart(key) 기준으로 매핑하고,
 * 이전에 없던 bucket이나 null이 낀 탐지율은 보간하지 않고 최종값으로 스냅한다.
 * point 수가 달라도 안전하다(문자열 path 보간 금지 정책).
 */
export function interpolateSeries(
  previous: OpsSeriesPoint[],
  next: OpsSeriesPoint[],
  t: number
): OpsSeriesPoint[] {
  if (t >= 1) {
    return next;
  }
  const prevByKey = new Map(previous.map((point) => [point.bucketStart, point]));
  const lerp = (a: number, b: number) => a + (b - a) * t;
  return next.map((point) => {
    const before = prevByKey.get(point.bucketStart);
    if (!before) {
      return point;
    }
    const rate =
      before.detectionRatePercent == null || point.detectionRatePercent == null
        ? point.detectionRatePercent
        : lerp(before.detectionRatePercent, point.detectionRatePercent);
    // partial 등 값 이외의 계약 필드는 최종(next) 것을 그대로 유지한다.
    return {
      ...point,
      collectedTransactions: lerp(before.collectedTransactions, point.collectedTransactions),
      detectedEvents: lerp(before.detectedEvents, point.detectedEvents),
      detectionRatePercent: rate
    };
  });
}

/** 두 시리즈가 같은 bucket 집합을 하나라도 공유하면 morph 대상이다. */
export function sharesBuckets(previous: OpsSeriesPoint[], next: OpsSeriesPoint[]): boolean {
  const prev = new Set(previous.map((point) => point.bucketStart));
  return next.some((point) => prev.has(point.bucketStart));
}

/**
 * tooltip 가로 위치(%): viewport 가장자리에서 잘리지 않게 클램프.
 * index 중앙 기준 백분율을 [minPercent, maxPercent]로 제한한다.
 */
export function clampTooltipPercent(
  index: number,
  length: number,
  minPercent = 10,
  maxPercent = 90
): number {
  if (length <= 0) {
    return 50;
  }
  const raw = ((index + 0.5) / length) * 100;
  return Math.min(maxPercent, Math.max(minPercent, raw));
}
