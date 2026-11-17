# 실행·설정·테스트

## 로컬 실행

Docker Desktop이 필요합니다. PostgreSQL, Redis, Kafka, AI 분석 서버, 백엔드, 프런트엔드가 함께 기동됩니다.

```bash
docker compose --profile app up --build -d
```

| 서비스 | 주소 |
|---|---|
| Web UI | http://127.0.0.1:8088 |
| Backend API | http://127.0.0.1:18080 |
| Swagger UI | http://127.0.0.1:18080/swagger-ui.html |

Grafana(3000)·Prometheus(9090)·Kafka UI(8081)는 `--profile monitoring`을 추가하면 함께 실행됩니다. 종료는 `docker compose --profile app down`이며 데이터 볼륨은 보존됩니다.

## 환경 설정

비밀값은 커밋하지 않고 환경변수로 주입합니다.

| 변수 | 설명 | 기본 상태 |
|---|---|---|
| `CHAINWATCH_COLLECTOR_ENABLED` | 자동 블록 수집 활성화 | `false` |
| `CHAINWATCH_COLLECTOR_PROVIDER` | `rpc` 또는 `etherscan` 선택 | `rpc` |
| `CHAINWATCH_ETHERSCAN_API_KEY` | Etherscan API 키 | 미설정 |
| `CHAINWATCH_AI_ENABLED` | AI 분석 연동 활성화 | `false` |
| `ANTHROPIC_API_KEY` / `GEMINI_API_KEY` | AI 제공자 인증 키 | 미설정 |
| `CHAINWATCH_JWT_ENABLED` | JWT 인증 활성화 (외부 공개 시 필수) | `false` |
| `CHAINWATCH_JWT_SECRET` / `CHAINWATCH_PASSWORD_PEPPER` | 인증 비밀값 | 개발용 기본값 |
| `CHAINWATCH_ADMIN_USERNAME` / `CHAINWATCH_ADMIN_PASSWORD` | 최초 관리자 계정 | 미설정 |

자동 수집, 웹훅 알림, 최소 알림 위험 점수는 **운영 관리 → 탐지·알림 정책**에서 재시작 없이 변경할 수 있고 변경 내역은 감사 로그에 남습니다.

## 개별 실행과 테스트

### Backend

```bash
cd backend
./gradlew bootRun
./gradlew test
```

### Frontend

```bash
cd frontend
npm ci
npm run dev
npm test
npm run build
```

### AI Analysis Server

```bash
cd ai/analysis-server
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements-dev.txt
uvicorn app.main:app --port 8000
pytest -q
```

## CI/CD

GitHub Actions가 `main` 브랜치와 Pull Request에서 백엔드, 프론트엔드, AI 분석 서버의 테스트와 빌드를 검증합니다. `main`의 CI가 성공하면 다음 Docker 이미지를 GitHub Container Registry에 게시합니다.

- `ghcr.io/bitofsyn/chainwatch-backend`
- `ghcr.io/bitofsyn/chainwatch-frontend`
- `ghcr.io/bitofsyn/chainwatch-ai-analysis`

각 이미지는 `latest`와 배포 원본을 식별할 수 있는 전체 Git 커밋 SHA 태그를 함께 제공합니다.

## 보안 주의사항

- `.env`, API 키, 개인 키, 인증서와 자격 증명 파일은 Git 추적 대상에서 제외됩니다.
- 인터넷에 공개하기 전 `CHAINWATCH_JWT_ENABLED=true`와 강한 관리자 비밀번호를 반드시 설정해야 합니다.
- Quick Tunnel에는 Web UI의 로컬 포트만 연결하고 PostgreSQL, Kafka, Redis, AI 서버 및 모니터링 포트는 공개하지 않습니다.
- 저장소의 기본 계정과 비밀번호는 로컬 개발 편의를 위한 값이며 운영 환경에서 사용하면 안 됩니다.
- 운영 환경에서는 JWT 비밀키, 비밀번호 pepper, 관리자 비밀번호와 Grafana 비밀번호를 반드시 교체해야 합니다.
- Webhook URL은 관리자 API 응답에서 마스킹되지만 데이터베이스에는 암호화되지 않은 상태로 저장됩니다. 실제 운영 환경에서는 애플리케이션 계정과 DB 접근을 제한하고 Secret Manager 연동을 권장합니다.
- AI API 키는 환경변수 또는 별도의 Secret Manager로 관리해야 합니다.
- AI가 생성한 내용은 참고 자료이며 제재 판단, 자금 동결과 같은 최종 조치에는 사람의 검토가 필요합니다.
- 공개 RPC는 호출 제한과 데이터 신뢰성 문제가 있을 수 있으므로 운영 환경에서는 전용 제공자와 모니터링을 권장합니다.
