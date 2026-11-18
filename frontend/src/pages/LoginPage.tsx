import { useState, type FormEvent } from "react";
import { ApiError } from "../api";
import { useAuth } from "../contexts/AuthContext";
import { navigate, roleHomePath } from "../lib/router";
import type { DemoRole, User } from "../types";

interface LoginPageProps {
  /** 로그인 성공 후 복귀할 해시 경로. "/"이면 역할별 첫 화면으로 보낸다. */
  nextPath: string;
}

const ROLE_GUIDE: { role: string; summary: string; tasks: string }[] = [
  {
    role: "조회자",
    summary: "탐지 현황을 확인합니다.",
    tasks: "대시보드, 이상 거래 목록과 탐지 근거, 적용 중인 탐지 규칙을 볼 수 있습니다."
  },
  {
    role: "분석가",
    summary: "탐지된 이벤트를 검토하고 처리합니다.",
    tasks: "담당자 지정, 접수 → 조사중 → 해결·오탐 종결, 운영 메모를 남길 수 있습니다."
  },
  {
    role: "관리자",
    summary: "탐지 시스템을 운영합니다.",
    tasks: "블록 수집, 탐지 임계값과 관심 주소, 알림 웹훅, 사용자 계정과 감사 로그를 관리합니다."
  }
];

export function LoginPage({ nextPath }: LoginPageProps) {
  const { login, demoLogin } = useAuth();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState<"form" | DemoRole | null>(null);
  const [error, setError] = useState<string | null>(null);

  function goAfterLogin(user: User) {
    // 특정 화면에서 로그인으로 밀려온 경우 그 화면으로, 아니면 역할별 첫 화면으로 보낸다.
    navigate(nextPath !== "/" ? nextPath : roleHomePath(user.role));
  }

  function describeFailure(cause: unknown, demo: boolean) {
    if (cause instanceof ApiError && cause.status === 401) {
      return demo
        ? "둘러보기가 비활성화되어 있습니다. 발급받은 계정으로 로그인해주세요."
        : "아이디 또는 비밀번호가 올바르지 않습니다.";
    }
    return "서버에 연결하지 못했습니다. 서버가 깨어나는 중일 수 있으니 1~2분 뒤 다시 시도해주세요.";
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setBusy("form");
    setError(null);
    try {
      goAfterLogin(await login(username, password));
    } catch (cause) {
      setError(describeFailure(cause, false));
    } finally {
      setBusy(null);
    }
  }

  async function handleDemo(role: DemoRole) {
    setBusy(role);
    setError(null);
    try {
      goAfterLogin(await demoLogin(role));
    } catch (cause) {
      setError(describeFailure(cause, true));
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="login-layout">
      <section className="login-intro">
        <p className="eyebrow">On-chain risk monitoring</p>
        <h1>온체인 이상 거래 관제</h1>
        <p className="page-lede">
          이더리움 거래를 수집해 재현 가능한 규칙으로 이상 징후를 탐지하고, 탐지 근거와 함께 검토·처리하는
          운영 콘솔입니다. 로그인한 역할에 따라 할 수 있는 일이 달라집니다.
        </p>
        <dl className="role-guide">
          {ROLE_GUIDE.map((item) => (
            <div key={item.role} className="role-guide-item">
              <dt>
                {item.role}
                <span>{item.summary}</span>
              </dt>
              <dd>{item.tasks}</dd>
            </div>
          ))}
        </dl>
      </section>

      <section className="glass-card login-card">
        <h2>둘러보기</h2>
        <p className="login-hint">계정 없이 데모 계정으로 바로 들어갑니다.</p>
        <div className="demo-actions">
          <button
            type="button"
            className="primary-button"
            onClick={() => handleDemo("VIEWER")}
            disabled={busy != null}
          >
            {busy === "VIEWER" ? "들어가는 중…" : "조회자로 둘러보기"}
          </button>
          <button
            type="button"
            className="ghost-button"
            onClick={() => handleDemo("ANALYST")}
            disabled={busy != null}
          >
            {busy === "ANALYST" ? "들어가는 중…" : "분석가로 둘러보기"}
          </button>
        </div>

        <div className="login-divider">
          <span>계정으로 로그인</span>
        </div>

        <form className="login-form" onSubmit={handleSubmit}>
          <label>
            아이디
            <input
              type="text"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              autoComplete="username"
              required
            />
          </label>
          <label>
            비밀번호
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
              required
            />
          </label>
          {error ? (
            <div className="banner error" role="alert">
              {error}
            </div>
          ) : null}
          <button type="submit" className="ghost-button" disabled={busy != null}>
            {busy === "form" ? "확인 중…" : "로그인"}
          </button>
        </form>
        <p className="login-hint">관리자 계정은 둘러보기로 제공하지 않습니다.</p>
      </section>
    </div>
  );
}
