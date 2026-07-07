const TOKEN_KEY = "chainwatch-admin-token";

export function getToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string) {
  sessionStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  sessionStorage.removeItem(TOKEN_KEY);
}

/**
 * 세션에 토큰이 있으면 운영 액션(상태 변경/분석 요청)을 시도할 수 있다.
 * 실제 권한(ADMIN/ANALYST)은 백엔드가 판정하며, 403은 각 화면에서 처리한다.
 */
export function isAuthenticated(): boolean {
  return getToken() !== null;
}

/** @deprecated 토큰 존재 여부만 확인한다. isAuthenticated()를 사용할 것. */
export function isAdmin(): boolean {
  return isAuthenticated();
}
