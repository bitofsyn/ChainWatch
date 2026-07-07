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

export type AdminSection = "dashboard" | "pipeline" | "analysis" | "policies" | "audit";

export function matchAdminSection(route: string): AdminSection | null {
  if (route === "/admin") {
    return "dashboard";
  }
  const match = route.match(/^\/admin\/(pipeline|analysis|policies|audit)$/);
  return match ? (match[1] as AdminSection) : null;
}
