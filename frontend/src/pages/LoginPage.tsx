import { useState, type FormEvent } from "react";
import { ApiError } from "../api";
import { useAuth } from "../contexts/AuthContext";
import { navigate } from "../lib/router";

interface LoginPageProps {
  /** 로그인 성공 후 복귀할 해시 경로 (기본 "/") */
  nextPath: string;
}

export function LoginPage({ nextPath }: LoginPageProps) {
  const { login } = useAuth();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await login(username, password);
      navigate(nextPath);
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) {
        setError("아이디 또는 비밀번호가 올바르지 않습니다.");
      } else {
        setError("로그인에 실패했습니다. 백엔드 상태를 확인해주세요.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <section className="page-head">
        <div>
          <p className="eyebrow">로그인</p>
          <h1>ChainWatch 콘솔 로그인</h1>
          <p className="page-lede">
            발급받은 운영 계정으로 로그인하세요. 계정이 없다면 관리자에게 발급을 요청해야 합니다.
          </p>
        </div>
      </section>

      <section className="glass-card login-card">
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
          {error ? <div className="banner error">{error}</div> : null}
          <button type="submit" className="primary-button" disabled={submitting}>
            {submitting ? "확인 중…" : "로그인"}
          </button>
        </form>
      </section>
    </>
  );
}
