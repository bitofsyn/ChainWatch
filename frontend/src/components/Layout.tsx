import type { ReactNode } from "react";
import type { Theme } from "../hooks/useTheme";
import { useAuth } from "../contexts/AuthContext";
import { useFeatureFlags, type FeatureFlags } from "../hooks/useFeatureFlags";
import { markExplicitLogout, navigate } from "../lib/router";
import type { Role } from "../types";

interface LayoutProps {
  route: string;
  theme: Theme;
  onToggleTheme: () => void;
  children: ReactNode;
}

interface NavItem {
  path: string;
  label: string;
}

const DASHBOARD: NavItem = { path: "/", label: "대시보드" };
const RULES: NavItem = { path: "/rules", label: "탐지 정책" };
/** 수집→탐지→분석→알림 단계별 처리량·병목 화면. 시스템 운영자(관리자)용이다. */
const PIPELINE_OPS: NavItem = { path: "/agents", label: "처리 단계 관제" };

/**
 * 역할별 메뉴. 첫 항목이 그 역할의 주 업무 화면이다.
 * 이상 거래 관제와 직접 관련 없는 선택 기능(AI 재분석, 검증인 조회)은 켜져 있을 때만 보인다.
 */
function navFor(role: Role, flags: FeatureFlags): NavItem[] {
  if (role === "VIEWER") {
    return [DASHBOARD, { path: "/events", label: "이상 거래" }, RULES];
  }
  if (role === "ANALYST") {
    return [
      { path: "/work", label: "작업 큐" },
      { path: "/events", label: "이상 거래" },
      DASHBOARD,
      RULES,
      ...(flags.aiEnabled ? [{ path: "/admin/analysis", label: "재분석" }] : [])
    ];
  }
  return [
    { path: "/admin", label: "운영 설정" },
    { path: "/work", label: "작업 큐" },
    DASHBOARD,
    { path: "/events", label: "이상 거래" },
    RULES,
    PIPELINE_OPS,
    ...(flags.stakingEnabled ? [{ path: "/staking", label: "검증인" }] : [])
  ];
}

/** 메뉴 아래에 표시하는 역할 안내. 로그인한 사람이 자기 권한 범위를 바로 알 수 있게 한다. */
const ROLE_SCOPE: Record<Role, string> = {
  VIEWER: "조회 전용입니다. 탐지 현황과 근거를 볼 수 있고, 이벤트 처리와 설정 변경은 할 수 없습니다.",
  ANALYST: "탐지된 이벤트를 검토하고 처리합니다. 탐지 기준과 수집 설정은 관리자가 변경합니다.",
  ADMIN: "수집, 탐지 기준, 알림, 사용자 계정을 관리합니다."
};

function isActive(route: string, path: string): boolean {
  if (path === "/") {
    return route === "/" || route.startsWith("/?");
  }
  // "/admin"(운영 설정)과 "/admin/analysis"(재분석)가 함께 있을 때 더 구체적인 쪽만 활성화한다.
  if (path === "/admin" && route.startsWith("/admin/analysis")) {
    return false;
  }
  return route === path || route.startsWith(`${path}/`) || route.startsWith(`${path}?`);
}

const ROLE_LABELS: Record<string, string> = {
  ADMIN: "관리자",
  ANALYST: "분석가",
  VIEWER: "조회자"
};

export function Layout({ route, theme, onToggleTheme, children }: LayoutProps) {
  const { user, logout } = useAuth();
  const flags = useFeatureFlags();

  async function handleLogout() {
    // 직접 로그아웃한 경우에는 다음 로그인(다른 계정일 수 있음)을 이전 화면으로 보내지 않는다.
    markExplicitLogout();
    await logout();
    navigate("/login");
  }

  return (
    <div className="app-shell">
      <header className="top-nav">
        <a className="brand" href="#/">
          <span className="brand-mark" aria-hidden="true" />
          ChainWatch
          <span className="brand-sub">온체인 이상 거래 탐지</span>
        </a>
        <nav className="nav-links" aria-label="주요 메뉴">
          {(user ? navFor(user.role, flags) : []).map((item) => (
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
        ) : null}
        <button
          type="button"
          className="ghost-button theme-toggle"
          onClick={onToggleTheme}
          aria-label="테마 전환"
        >
          {theme === "dark" ? "☀️ 라이트" : "🌙 다크"}
        </button>
      </header>
      {user ? (
        <p className="role-scope">
          <strong>{ROLE_LABELS[user.role] ?? user.role}</strong>
          {ROLE_SCOPE[user.role]}
        </p>
      ) : null}
      <main className="page-body">{children}</main>
    </div>
  );
}
