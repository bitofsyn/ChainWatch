/**
 * 토큰 저장소. sessionStorage 사용(탭 닫으면 소멸) — XSS에 노출되면 토큰도 노출되는
 * 트레이드오프는 내부 운영 콘솔 특성상 수용한다(프로젝트 결정).
 * 액세스 토큰(60분)과 회전식 리프레시 토큰(14일)을 분리 보관한다.
 */
const ACCESS_TOKEN_KEY = "chainwatch-access-token";
const REFRESH_TOKEN_KEY = "chainwatch-refresh-token";
/** 구버전 단일 토큰 키 — 발견 시 제거만 한다(재사용 불가: 응답 형식이 다름). */
const LEGACY_TOKEN_KEY = "chainwatch-admin-token";

export function getAccessToken(): string | null {
  sessionStorage.removeItem(LEGACY_TOKEN_KEY);
  return sessionStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  return sessionStorage.getItem(REFRESH_TOKEN_KEY);
}

export function setTokens(accessToken: string, refreshToken: string) {
  sessionStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  sessionStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
}

export function clearTokens() {
  sessionStorage.removeItem(ACCESS_TOKEN_KEY);
  sessionStorage.removeItem(REFRESH_TOKEN_KEY);
  sessionStorage.removeItem(LEGACY_TOKEN_KEY);
}

export function hasTokens(): boolean {
  return getAccessToken() !== null;
}
