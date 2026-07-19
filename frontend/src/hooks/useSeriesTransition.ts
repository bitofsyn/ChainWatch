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
      // 숨김 탭에서는 rAF가 멈춰 morph가 시작값에 동결되므로 즉시 최종 상태로 스냅한다.
      (typeof document !== "undefined" && document.visibilityState === "hidden") ||
      !sharesBuckets(displayRef.current, series)
    ) {
      snap();
      return;
    }

    const from = displayRef.current;
    let frame: number | null = null;
    let start: number | null = null;
    let settled = false;
    const step = (timestamp: number) => {
      if (settled) {
        return;
      }
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
        settled = true;
        frame = null;
      }
    };
    frame = requestAnimationFrame(step);
    // rAF가 오지 않는 환경(탭 전환·임베디드 브라우저)에서도 최종 상태 도달을 보장한다.
    const settleTimer = setTimeout(() => {
      if (!settled) {
        settled = true;
        snap();
      }
    }, MORPH_DURATION_MS + 150);
    return () => {
      settled = true;
      if (frame != null) {
        cancelAnimationFrame(frame);
      }
      clearTimeout(settleTimer);
    };
  }, [series, queryKey, reducedMotion]);

  return display;
}
