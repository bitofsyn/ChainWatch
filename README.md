# ChainWatch

AI 기반 온체인 이상거래 탐지 시스템.

Ethereum 트랜잭션을 실시간으로 수집하고, 룰 기반 엔진으로 이상거래(고액 이체, 반복 이체, 거래소 입출금, 워치리스트 활동)를 탐지한 뒤, AI가 자연어 리포트로 요약해 대시보드로 제공합니다.

상세 설계 기준은 [CHAINWATCH_IMPLEMENTATION_PROCESS.md](CHAINWATCH_IMPLEMENTATION_PROCESS.md)를 따릅니다.

## 아키텍처

```
Ethereum / Etherscan API
        ↓
Collector (Spring Boot)  ──▶ Kafka ──▶ Detection (Rule Engine)
                                          ↓
                              PostgreSQL / Redis (피드 캐시)
                                          ↓
                              AI Analysis Server (FastAPI)
                                          ↓
                              REST API (JWT/Swagger) ──▶ React Dashboard
```

## 저장소 구조

| 경로 | 설명 |
|---|---|
| `backend/` | Spring Boot 3 / Java 21 — Collector, Detection, REST API, 인증 |
| `ai/analysis-server/` | FastAPI — AI 분석 리포트 생성 (Claude/Gemini/LM Studio/Hermes 어댑터 + Retry/Fallback) |
| `frontend/` | React + TypeScript + Vite 대시보드 |
| `docs/` | 구현 계획 및 로컬 인프라 문서 |
| `docker-compose.yml` | PostgreSQL, Redis, Kafka(KRaft), Kafka UI, AI 분석 서버 |

## 빠른 시작

### 1. 로컬 인프라

```bash
docker compose up -d
```

- PostgreSQL `localhost:55432` (chainwatch/chainwatch)
- Redis `localhost:6379`, Kafka `localhost:9094`, Kafka UI `localhost:8081`
- AI Analysis Server `localhost:8000` (기본 mock 프로바이더)

### 2. 백엔드

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'
```

기본 프로필은 H2 인메모리, `local` 프로필은 PostgreSQL/Redis/Kafka를 사용합니다.

### 3. AI 분석 서버 (직접 실행 시)

```bash
cd ai/analysis-server
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --port 8000
```

### 4. 프론트엔드

```bash
cd frontend
npm install
npm run dev
```

## 인증 (선택)

JWT는 기본 비활성화되어 있습니다. 활성화:

```yaml
chainwatch:
  security:
    jwt-enabled: true
```

- 로그인: `POST /api/auth/login` `{"username":"admin","password":"chainwatch"}` (환경변수 `CHAINWATCH_ADMIN_*`으로 교체)
- Swagger: `http://localhost:8080/swagger-ui.html`

## 테스트

```bash
cd backend && ./gradlew test          # 백엔드
cd ai/analysis-server && pytest      # AI 서버
```

## 문서

- [CHANGELOG.md](CHANGELOG.md) — 변경 이력
- [TODO.md](TODO.md) — 남은 작업
- [docs/BACKEND_IMPLEMENTATION_PLAN.md](docs/BACKEND_IMPLEMENTATION_PLAN.md) — 백엔드 구현 체크리스트
- [docs/LOCAL_INFRA_SETUP.md](docs/LOCAL_INFRA_SETUP.md) — 로컬 인프라 상세
- [ai/analysis-server/README.md](ai/analysis-server/README.md) — AI 서버 상세
