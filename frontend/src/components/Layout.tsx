import type { ReactNode } from "react";
import type { Theme } from "../hooks/useTheme";
import { useAuth } from "../contexts/AuthContext";
import { navigate } from "../lib/router";

interface LayoutProps {
  route: string;
  theme: Theme;
  onToggleTheme: () => void;
  children: ReactNode;
}

const NAV_ITEMS = [
  { path: "/", label: "관제 현황" },
  { path: "/events", label: "이상거래" },
  { path: "/agents", label: "Agent 콘솔" },
  { path: "/rules", label: "탐지 기준" },
  { path: "/admin", label: "관리자" }
];

function isActive(route: string, path: string): boolean {
  if (path === "/") {
    return route === "/";
  }
  return route === path || route.startsWith(`${path}/`);
}

const ROLE_LABELS: Record<string, string> = {
  ADMIN: "관리자",
  ANALYST: "분석가"
};

export function Layout({ route, theme, onToggleTheme, children }: LayoutProps) {
  const { user, logout } = useAuth();

  async function handleLogout() {
    await logout();
    navigate("/");
  }

  return (
    <div className="app-shell">
      <header className="top-nav">
        <a className="brand" href="#/">
          <span className="brand-mark" aria-hidden="true" />
          ChainWatch
          <span className="brand-sub">온체인 리스크 관제</span>
        </a>
        <nav className="nav-links" aria-label="주요 메뉴">
          {NAV_ITEMS.map((item) => (
            <a
              key={item.path}
              href={`#${item.path}`}
              className={`nav-link ${isActive(route, item.path) ? "active" : ""}`}
            >
              {item.label}
            </a>
          ))}
        </nav>
        {user ? (
          <div className="nav-user">
            <span className="nav-user-name" title={user.username}>
              {user.displayName || user.username}
              <small>{ROLE_LABELS[user.role] ?? user.role}</small>
            </span>
            <button type="button" className="ghost-button" onClick={handleLogout}>
              로그아웃
            </button>
          </div>
        ) : (
          <a className="ghost-button" href={`#/login?next=${encodeURIComponent(route)}`}>
            로그인
          </a>
        )}
        <button
          type="button"
          className="ghost-button theme-toggle"
          onClick={onToggleTheme}
          aria-label="테마 전환"
        >
          {theme === "dark" ? "☀️ 라이트" : "🌙 다크"}
        </button>
      </header>
      <main className="page-body">{children}</main>
    </div>
  );
}
