// Cloudflare Workers 진입점. 정적 자산은 그대로 제공하고 /api 요청만 백엔드로 전달한다.
// 프런트엔드가 같은 출처의 /api를 호출하므로(nginx.conf와 동일한 구조) CORS 설정이 필요 없다.
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (!url.pathname.startsWith("/api/")) {
      return env.ASSETS.fetch(request);
    }
    if (!env.BACKEND_URL) {
      return Response.json(
        { message: "BACKEND_URL이 설정되지 않았습니다." },
        { status: 503 },
      );
    }

    const target = new URL(url.pathname + url.search, env.BACKEND_URL);
    const headers = new Headers(request.headers);
    headers.set("X-Forwarded-Host", url.host);
    headers.set("X-Forwarded-Proto", url.protocol.replace(":", ""));

    return fetch(target, {
      method: request.method,
      headers,
      body: request.body,
      redirect: "manual",
    });
  },
};
