# ChainWatch 운영 가이드

## 구성 요소

| 서비스 | 이미지/빌드 | 포트 | 비고 |
|---|---|---|---|
| backend | `backend/Dockerfile` (멀티스테이지 Gradle) | 8080 | `app` 프로필로 기동 |
| frontend | `frontend/Dockerfile` (Vite 빌드 → Nginx) | 80 | `/api` 리버스 프록시 포함 |
| ai-analysis | `ai/analysis-server/Dockerfile` | 8000 | 기본 mock 프로바이더 |
| postgres / redis / kafka | 공식 이미지 | 55432 / 6379 / 9094 | healthcheck 구성 |
| prometheus | prom/prometheus | 9090 | backend `/actuator/prometheus` 스크레이프 |
| grafana | grafana/grafana | 3000 | Prometheus 데이터소스 자동 프로비저닝 |

## 로컬 전체 스택 기동

```bash
# 인프라만 (기본): postgres, redis, kafka, kafka-ui, ai-analysis, prometheus, grafana
docker compose up -d

# 앱 포함 (backend/frontend 컨테이너 빌드+기동)
docker compose --profile app up -d --build
```

- 대시보드: http://localhost (frontend 컨테이너 기동 시) 또는 `npm run dev`(5173)
- Grafana: http://localhost:3000 (admin / chainwatch — `GRAFANA_ADMIN_PASSWORD`로 변경)
- Prometheus: http://localhost:9090

## Health Check

| 대상 | 엔드포인트 |
|---|---|
| backend | `GET /actuator/health` (컨테이너 HEALTHCHECK 내장) |
| ai-analysis | `GET /health` (컨테이너 HEALTHCHECK 내장) |
| frontend | `GET /` (Nginx) |

## 메트릭 / 모니터링

- backend는 micrometer-registry-prometheus로 `/actuator/prometheus`를 노출합니다.
- Prometheus 스크레이프 대상은 `infra/monitoring/prometheus.yml`에서 관리합니다.
  - 로컬 개발(호스트에서 backend 실행): `host.docker.internal:8080` (기본값)
  - 전체 컨테이너 배포: 대상을 `backend:8080`으로 교체
- Grafana 데이터소스는 `infra/monitoring/grafana/provisioning`으로 자동 등록됩니다.
  JVM 대시보드는 Grafana 커뮤니티 대시보드 ID `4701`(JVM Micrometer) 임포트를 권장합니다.

## 로깅

- `backend/src/main/resources/logback-spring.xml`에서 포맷 통일:
  `시간 레벨 [스레드] 로거 - 메시지`
- 애플리케이션 로그는 `key=value` 스타일 구조화 메시지를 사용합니다
  (예: `notification sent | channel=slack eventId=1 riskScore=90`).

## CI (GitHub Actions)

| 워크플로 | 트리거 | 수행 |
|---|---|---|
| `backend.yml` | backend/** 변경 | Gradle build + test (실패 시 리포트 아티팩트) |
| `frontend.yml` | frontend/** 변경 | npm ci → vitest → vite build |
| `ai-analysis-server.yml` | ai/analysis-server/** 변경 | pip install → pytest |

## AWS 배포 (EC2 기준 절차)

1. EC2(Ubuntu 22.04+, t3.medium 이상) 생성, 보안그룹에서 80/443만 공개 (8080/9090/3000은 내부 전용 권장).
2. Docker + docker compose 플러그인 설치.
3. 저장소 클론 후 환경 변수 설정:
   ```bash
   export CHAINWATCH_JWT_SECRET=$(openssl rand -hex 32)
   export CHAINWATCH_ADMIN_PASSWORD='{bcrypt}...'   # spring-boot-cli encodepassword 등으로 생성
   export ANTHROPIC_API_KEY=...                      # AI 실 프로바이더 사용 시
   export GRAFANA_ADMIN_PASSWORD=...
   ```
4. `docker compose --profile app up -d --build`
5. HTTPS는 frontend Nginx 앞단에 ALB + ACM 인증서 또는 certbot을 구성합니다.

### 운영 체크리스트

- [ ] `chainwatch.security.jwt-enabled=true` 및 시크릿/관리자 비밀번호 교체
- [ ] `chainwatch.notification.enabled=true` + Slack/Discord Webhook URL 설정
- [ ] `chainwatch.collector.enabled=true` + RPC/Etherscan 키 설정
- [ ] Prometheus 대상 `backend:8080`으로 전환, Grafana 알림 규칙 구성
- [ ] PostgreSQL 볼륨 백업 정책 수립
