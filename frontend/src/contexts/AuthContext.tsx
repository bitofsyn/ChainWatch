import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode
} from "react";
import { fetchMe, login as loginApi, logoutSession, LOGOUT_EVENT } from "../api";
import { clearTokens, getRefreshToken, hasTokens, setTokens } from "../lib/auth";
import type { User } from "../types";

interface AuthContextValue {
  /** 로그인된 사용자. jwt-enabled=false 개발 모드에서 비로그인 시 null(익명)이며 API는 공개다. */
  user: User | null;
  /** 초기 /me 확인이 끝나기 전에는 "loading". 가드/리다이렉트는 ready 이후에만 판단할 것. */
  status: "loading" | "ready";
  isAdmin: boolean;
  login: (username: string, password: string) => Promise<User>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [status, setStatus] = useState<"loading" | "ready">(hasTokens() ? "loading" : "ready");

  // 토큰이 있으면 /me로 세션 복원. 401(만료·개발 모드 익명)은 조용히 익명 처리.
  useEffect(() => {
    let active = true;
    if (!hasTokens()) {
      return;
    }
    fetchMe()
      .then((me) => {
        if (active) {
          setUser(me);
        }
      })
      .catch(() => {
        if (active) {
          clearTokens();
        }
      })
      .finally(() => {
        if (active) {
          setStatus("ready");
        }
      });
    return () => {
      active = false;
    };
  }, []);

  // requestJson이 리프레시 실패로 세션을 정리했을 때 전역 상태를 동기화한다.
  useEffect(() => {
    const onLogout = () => setUser(null);
    window.addEventListener(LOGOUT_EVENT, onLogout);
    return () => window.removeEventListener(LOGOUT_EVENT, onLogout);
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const result = await loginApi(username, password);
    setTokens(result.accessToken, result.refreshToken);
    setUser(result.user);
    return result.user;
  }, []);

  const logout = useCallback(async () => {
    const refreshToken = getRefreshToken();
    if (refreshToken) {
      // 서버 폐기는 최선 노력: 실패해도 로컬 세션은 지운다
      try {
        await logoutSession(refreshToken);
      } catch {
        // ignore
      }
    }
    clearTokens();
    setUser(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ user, status, isAdmin: user?.role === "ADMIN", login, logout }),
    [user, status, login, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
}
