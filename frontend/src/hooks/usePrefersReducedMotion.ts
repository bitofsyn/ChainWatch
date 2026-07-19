import { useSyncExternalStore } from "react";

const QUERY = "(prefers-reduced-motion: reduce)";

function subscribe(onChange: () => void): () => void {
  const media = window.matchMedia(QUERY);
  media.addEventListener("change", onChange);
  return () => media.removeEventListener("change", onChange);
}

function getSnapshot(): boolean {
  return window.matchMedia(QUERY).matches;
}

/**
 * JS 기반 애니메이션(rAF count-up, 시리즈 morph 등)도 사용자 모션 설정을 따르게 하는 공용 훅.
 * true면 애니메이션을 논리적으로 비활성화하고 즉시 최종 상태를 보여줘야 한다.
 */
export function usePrefersReducedMotion(): boolean {
  return useSyncExternalStore(subscribe, getSnapshot, () => false);
}
