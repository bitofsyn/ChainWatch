import { useEffect, useRef, useState } from "react";
import { usePrefersReducedMotion } from "../hooks/usePrefersReducedMotion";
import type { ValueChange } from "../lib/overviewDiff";

interface AnimatedMetricValueProps {
  /** 현재 값. null이면 포맷터가 "—"를 만든다. */
  value: number | null;
  /** 단위·소수점 정책을 보존하는 포맷터 (호출부 소유) */
  format: (value: number | null) => string;
  /**
   * 직전 성공 응답과의 diff. up/down일 때만 count transition을 실행한다.
   * 최초 로드(initial)·미변경(same)·stale 유지 상태에서는 즉시 최종값을 보여준다.
   */
  change: ValueChange | null;
}

const COUNT_DURATION_MS = 400;

/** cubic ease-out — 중간 정수를 모두 DOM에 그리지 않고 rAF로만 갱신한다. */
function easeOut(t: number): number {
  return 1 - Math.pow(1 - t, 3);
}

/**
 * KPI 숫자 count-up/down. 스크린리더에는 중간값이 아니라 최종값만 알린다.
 * reduced motion에서는 논리적으로 비활성화되어 즉시 최종 상태를 보여준다.
 */
export function AnimatedMetricValue({ value, format, change }: AnimatedMetricValueProps) {
  const reducedMotion = usePrefersReducedMotion();
  const [display, setDisplay] = useState<number | null>(value);
  const frameRef = useRef<number | null>(null);

  const shouldAnimate =
    !reducedMotion &&
    change != null &&
    (change.direction === "up" || change.direction === "down") &&
    change.previous != null &&
    change.current != null;

  useEffect(() => {
    if (frameRef.current != null) {
      cancelAnimationFrame(frameRef.current);
      frameRef.current = null;
    }
    if (!shouldAnimate) {
      setDisplay(value);
      return;
    }
    const from = change.previous as number;
    const to = change.current as number;
    let start: number | null = null;
    const step = (timestamp: number) => {
      if (start == null) {
        start = timestamp;
      }
      const t = Math.min(1, (timestamp - start) / COUNT_DURATION_MS);
      setDisplay(from + (to - from) * easeOut(t));
      if (t < 1) {
        frameRef.current = requestAnimationFrame(step);
      } else {
        frameRef.current = null;
      }
    };
    frameRef.current = requestAnimationFrame(step);
    return () => {
      if (frameRef.current != null) {
        cancelAnimationFrame(frameRef.current);
        frameRef.current = null;
      }
    };
    // change.changedAt으로 키를 잡아 같은 값의 재렌더에서 애니메이션이 반복되지 않게 한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [change?.changedAt, value, shouldAnimate]);

  // 진행 중 표시값은 스크린리더에서 숨기고 최종값만 노출한다.
  return (
    <>
      <span aria-hidden="true">{format(display)}</span>
      <span className="sr-only">{format(value)}</span>
    </>
  );
}
