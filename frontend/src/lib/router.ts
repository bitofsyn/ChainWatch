import { useEffect, useState } from "react";

function currentRoute(): string {
  const hash = window.location.hash.replace(/^#/, "");
  return hash || "/";
}

export function useHashRoute(): string {
  const [route, setRoute] = useState(currentRoute);

  useEffect(() => {
    const onChange = () => setRoute(currentRoute());
    window.addEventListener("hashchange", onChange);
    return () => window.removeEventListener("hashchange", onChange);
  }, []);

  return route;
}

export function navigate(path: string) {
  window.location.hash = path;
}

/** Overview 라우트("/", "/?range=6h") 매칭. 쿼리 문자열(없으면 "")을 반환한다. */
export function matchOverview(route: string): string | null {
  const match = route.match(/^\/(?:\?(.*))?$/);
  return match ? (match[1] ?? "") : null;
}

const OVERVIEW_RANGES = ["1h", "6h", "24h"] as const;
export type OverviewRange = (typeof OVERVIEW_RANGES)[number];

/**
 * Overview의 선택 range를 URL에 보존한다: 새로고침/공유/뒤로가기에서 유지.
 * 알 수 없는 값은 null(호출부에서 기본값 적용).
 */
export function parseOverviewRange(route: string): OverviewRange | null {
  const query = matchOverview(route);
  if (query == null) {
    return null;
  }
  const value = new URLSearchParams(query).get("range");
  return (OVERVIEW_RANGES as readonly string[]).includes(value ?? "")
    ? (value as OverviewRange)
    : null;
}

/** range 선택을 담은 Overview 경로. 기본값(24h)은 쿼리 없이 유지한다. */
export function overviewPath(range: OverviewRange): string {
  return range === "24h" ? "/" : `/?range=${range}`;
}

/**
 * 이벤트 목록 라우트 매칭. "/events" 또는 "/events?riskLevel=..." 형태를 허용하고
 * 쿼리 문자열(없으면 "")을 반환한다. 매트릭스 셀 등에서 필터 딥링크에 쓴다.
 */
export function matchEventsList(route: string): string | null {
  const match = route.match(/^\/events(?:\?(.*))?$/);
  return match ? (match[1] ?? "") : null;
}

export function matchEventDetail(route: string): number | null {
  const match = route.match(/^\/events\/(\d+)$/);
  return match ? Number(match[1]) : null;
}

export function matchTransactionDetail(route: string): number | null {
  const match = route.match(/^\/transactions\/(\d+)$/);
  return match ? Number(match[1]) : null;
}

export function matchWalletDetail(route: string): string | null {
  const match = route.match(/^\/wallets\/([^/]+)$/);
  return match ? decodeURIComponent(match[1]) : null;
}

export function matchAgentTeamDetail(route: string): string | null {
  const match = route.match(/^\/agents\/teams\/([^/]+)$/);
  return match ? decodeURIComponent(match[1]) : null;
}

export type AdminSection = "dashboard" | "pipeline" | "analysis" | "policies" | "audit" | "users";

export function matchAdminSection(route: string): AdminSection | null {
  if (route === "/admin") {
    return "dashboard";
  }
  const match = route.match(/^\/admin\/(pipeline|analysis|policies|audit|users)$/);
  return match ? (match[1] as AdminSection) : null;
}

/** #/login?next=%2Fadmin 형태에서 로그인 후 복귀 경로를 얻는다. */
export function loginNextPath(route: string): string {
  const match = route.match(/^\/login(?:\?next=(.+))?$/);
  if (!match) {
    return "/";
  }
  const next = match[1] ? decodeURIComponent(match[1]) : "/";
  // 외부 URL 주입 방지: 해시 라우트 내부 경로만 허용
  return next.startsWith("/") ? next : "/";
}

export function isLoginRoute(route: string): boolean {
  return route === "/login" || route.startsWith("/login?");
}
