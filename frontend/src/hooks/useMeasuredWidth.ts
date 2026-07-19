import { useCallback, useEffect, useState } from "react";

/**
 * 엘리먼트의 렌더 폭(px)을 ResizeObserver로 추적한다.
 * SVG 차트가 고정 viewBox를 확대(글자·선 두께 뻥튀기)하는 대신
 * 폭을 1:1로 그려 어떤 화면에서도 텍스트가 실제 px 크기로 렌더링되게 한다.
 *
 * ref는 callback ref다 — 로딩 empty-state 등으로 대상 엘리먼트가 늦게
 * 마운트되어도 그 시점부터 관찰을 시작한다(useRef+effect 조합의 null 캡처 함정 회피).
 */
export function useMeasuredWidth(fallback: number): {
  ref: (element: HTMLElement | null) => void;
  width: number;
} {
  const [element, setElement] = useState<HTMLElement | null>(null);
  const [width, setWidth] = useState(fallback);

  useEffect(() => {
    if (!element) {
      return;
    }
    // 마운트 시 즉시 실측 + 이후 변화는 ResizeObserver.
    // 일부 임베디드 브라우저는 RO 콜백이 오지 않아 window resize를 폴백으로 둔다.
    const measure = () => {
      const measured = element.getBoundingClientRect().width;
      if (measured > 0) {
        setWidth(Math.round(measured));
      }
    };
    measure();
    let observer: ResizeObserver | null = null;
    if (typeof ResizeObserver !== "undefined") {
      observer = new ResizeObserver(measure);
      observer.observe(element);
    }
    window.addEventListener("resize", measure);
    return () => {
      observer?.disconnect();
      window.removeEventListener("resize", measure);
    };
  }, [element]);

  const ref = useCallback((node: HTMLElement | null) => {
    setElement(node);
  }, []);

  return { ref, width };
}
