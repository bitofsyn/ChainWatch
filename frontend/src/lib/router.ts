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

/**
 * 화면 이동은 해시(#/work)로만 하므로 주소의 경로는 항상 앱 루트여야 한다.
 * "/login"처럼 경로로 들어오면 그 경로가 주소창에 계속 남아 "/login#/work"가 되므로,
 * 루트가 아닌 경로로 들어왔을 때 바꿔 쓸 주소를 돌려준다. 이미 루트면 null.
 */
export function rootedEntryUrl(pathname: string, search: string, hash: string, base = "/"): string | null {
  if (pathname === base || pathname === `${base}index.html`) {
    return null;
  }
  return `${base}${search}${hash}`;
}

/** 앱을 띄우기 전에 한 번 호출해 주소의 경로를 앱 루트로 맞춘다(페이지를 다시 불러오지 않는다). */
export function normalizeEntryPath(base = "/") {
  const { pathname, search, hash } = window.location;
  const rooted = rootedEntryUrl(pathname, search, hash, base);
  if (rooted !== null) {
    window.history.replaceState(null, "", rooted);
  }
}

let explicitLogout = false;

/** 사용자가 직접 로그아웃했음을 표시한다. 세션 만료로 밀려난 경우와 구분하기 위한 1회성 표시다. */
export function markExplicitLogout() {
  explicitLogout = true;
}

/** 직접 로그아웃 표시를 읽고 지운다. true면 로그인 후 이전 화면으로 복귀시키지 않는다. */
export function consumeExplicitLogout(): boolean {
  const value = explicitLogout;
  explicitLogout = false;
  return value;
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

export type AdminSection =
  | "dashboard"
  | "collection"
  | "pipeline"
  | "analysis"
  | "policies"
  | "audit"
  | "users";

export function matchAdminSection(route: string): AdminSection | null {
  if (route === "/admin") {
    return "dashboard";
  }
  const match = route.match(/^\/admin\/(collection|pipeline|analysis|policies|audit|users)$/);
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

/** 로그인 직후 역할별 첫 화면. 관리자는 운영 설정, 분석가는 작업 큐, 조회자는 대시보드. */
export function roleHomePath(role: string | null | undefined): string {
  if (role === "ADMIN") {
    return "/admin";
  }
  if (role === "ANALYST") {
    return "/work";
  }
  return "/";
}
