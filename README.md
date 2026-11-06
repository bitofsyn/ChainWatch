# ChainWatch

AI를 활용해 블록체인 거래 데이터를 분석하고 이상 징후를 탐지하는 시스템입니다.

## 구성

- `backend/`: Java 21, Spring Boot 기반 API 및 탐지 파이프라인
- `frontend/`: React, TypeScript 기반 웹 UI
- `ai/analysis-server/`: Python, FastAPI 기반 AI 분석 서버
- `infra/monitoring/`: Prometheus 및 Grafana 설정
- `docker-compose.yml`: 로컬 통합 실행 환경

## 실행

Docker Desktop이 필요합니다.

```bash
docker compose --profile app up --build
```

- 웹 UI: http://localhost
- 백엔드 API: http://127.0.0.1:18080
- AI 분석 서버: http://127.0.0.1:8000
- Grafana: http://127.0.0.1:3000

API 키와 운영용 인증 정보는 저장소에 보관하지 않습니다. 필요한 값은 실행 전에 환경변수로 설정하세요.

```bash
export ANTHROPIC_API_KEY="..."
export GEMINI_API_KEY="..."
export CHAINWATCH_ETHERSCAN_API_KEY="..."
export CHAINWATCH_JWT_SECRET="..."
export CHAINWATCH_PASSWORD_PEPPER="..."
```

AI 분석과 블록 수집은 기본적으로 비활성화되어 있습니다. 필요할 때 다음 환경변수를 설정합니다.

```bash
export CHAINWATCH_AI_ENABLED=true
export CHAINWATCH_COLLECTOR_ENABLED=true
```

## 테스트

```bash
(cd backend && ./gradlew test)
npm --prefix frontend ci
npm --prefix frontend test
npm --prefix frontend run build
(cd ai/analysis-server && python -m pip install -r requirements-dev.txt && pytest -q)
```

## 보안

`.env`, 개인 키, 인증서, 자격 증명 파일은 `.gitignore`로 제외됩니다. 운영 비밀값은 환경변수 또는 별도의 비밀 관리 서비스를 사용하세요.
