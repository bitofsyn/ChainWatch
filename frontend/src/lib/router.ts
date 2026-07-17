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
