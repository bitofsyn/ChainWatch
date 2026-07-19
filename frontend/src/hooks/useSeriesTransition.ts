import { useEffect, useRef, useState } from "react";
import { interpolateSeries, sharesBuckets } from "../lib/chartGeometry";
import type { OpsSeriesPoint } from "../types";

const MORPH_DURATION_MS = 320;

function easeOut(t: number): number {
  return 1 - Math.pow(1 - t, 3);
}

/**
 * 같은 query(range/bucket)의 polling 갱신에서 기존 point가 새 위치로
 * 250~400ms morph하도록 표시용 시리즈를 보간한다.
 * - queryKey가 바뀌면(range 변경) morph하지 않고 즉시 교체한다(crossfade는 CSS 담당).
 * - bucket 집합이 전혀 겹치지 않으면 보간 없이 교체한다.
 * - reduced motion에서는 항상 즉시 최종 상태.
 */
export function useSeriesTransition(
  series: OpsSeriesPoint[],
  queryKey: string,
  reducedMotion: boolean
): OpsSeriesPoint[] {
  const [display, setDisplay] = useState(series);
  const displayRef = useRef(series);
  const lastTargetRef = useRef<{ series: OpsSeriesPoint[]; queryKey: string } | null>(null);

  useEffect(() => {
    const last = lastTargetRef.current;
    lastTargetRef.current = { series, queryKey };

    const snap = () => {
      displayRef.current = series;
      setDisplay(series);
    };

    if (
      last == null ||
      last.queryKey !== queryKey ||
      reducedMotion ||
      !sharesBuckets(displayRef.current, series)
    ) {
      snap();
      return;
    }

    const from = displayRef.current;
    let frame: number | null = null;
    let start: number | null = null;
    const step = (timestamp: number) => {
      if (start == null) {
        start = timestamp;
      }
      const t = Math.min(1, (timestamp - start) / MORPH_DURATION_MS);
      const next = interpolateSeries(from, series, easeOut(t));
      displayRef.current = next;
      setDisplay(next);
      if (t < 1) {
        frame = requestAnimationFrame(step);
      } else {
        frame = null;
      }
    };
    frame = requestAnimationFrame(step);
    return () => {
      if (frame != null) {
        cancelAnimationFrame(frame);
      }
    };
  }, [series, queryKey, reducedMotion]);

  return display;
}
