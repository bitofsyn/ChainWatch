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
| `CHAINWATCH_COLLECTOR_MAX_LAG_BLOCKS` | 수집이 이 블록 수보다 더 밀리면 밀린 구간을 건너뛰고 최신 블록부터 수집 (`0`이면 건너뛰지 않음) | `100` |
| `CHAINWATCH_COLLECTOR_PROVIDER` | `rpc` 또는 `etherscan` 선택 | `rpc` |
| `CHAINWATCH_ETHERSCAN_API_KEY` | Etherscan API 키 | 미설정 |
| `CHAINWATCH_AI_ENABLED` | AI 분석 연동 활성화 | `false` |
| `ANTHROPIC_API_KEY` / `GEMINI_API_KEY` | AI 제공자 인증 키 | 미설정 |
| `CHAINWATCH_JWT_ENABLED` | JWT 인증 활성화 (외부 공개 시 필수) | `false` |
| `CHAINWATCH_JWT_SECRET` / `CHAINWATCH_PASSWORD_PEPPER` | 인증 비밀값 | 개발용 기본값 |
| `CHAINWATCH_ADMIN_USERNAME` / `CHAINWATCH_ADMIN_PASSWORD` | 최초 관리자 계정 | 미설정 |
| `CHAINWATCH_DEMO_LOGIN_ENABLED` | 로그인 화면의 둘러보기(비밀번호 없는 조회자·분석가 데모 세션) 허용 | `true` |
| `CHAINWATCH_ANALYST_PASSWORD` | 최초 분석가 계정 비밀번호. JWT 인증이 켜진 상태에서 개발 기본값이면 계정을 만들지 않음 | 개발용 기본값 |

자동 수집, 웹훅 알림, 최소 알림 위험 점수는 **운영 관리 → 탐지·알림 정책**에서 재시작 없이 변경할 수 있고 변경 내역은 감사 로그에 남습니다.

## 역할

모든 화면은 로그인이 필요합니다. 로그인하면 역할에 따라 메뉴와 첫 화면이 달라집니다.

| 역할 | 첫 화면 | 할 수 있는 일 |
|---|---|---|
| 조회자 (`VIEWER`) | 대시보드 | 대시보드, 이상 거래와 탐지 근거, 탐지 정책 조회 |
| 분석가 (`ANALYST`) | 작업 큐 | 조회 + 이벤트 담당자 지정·상태 변경·메모, AI 분석 요청 |
| 관리자 (`ADMIN`) | 운영 설정 | 분석가 권한 + 블록 수집, 탐지 임계값·주소 목록, 알림 웹훅, 사용자 관리, 감사 로그 |

### 관리자 화면(운영 설정)

| 탭 | 내용 |
|---|---|
| 개요 | 수집, 탐지 기준, 알림, 예외 주소 제안, 사용자, 시스템 상태의 현재 값을 카드로 표시하고 각 설정으로 이동 |
| 수집·알림 | 수동 블록 수집, 자동 수집 켜기·끄기, 웹훅 알림과 최소 위험 점수 |
| 탐지 기준 | 적용 중인 규칙, 분석가의 예외 주소 제안 검토, 임계값·주소 목록 수정. 관심 주소·반복 이체·자금 분산은 같은 지갑을 재탐지 억제 시간(기본 30분) 동안 이벤트 1건으로 묶는다 |
| 사용자 관리 · 감사 로그 | 계정 생성·역할 변경·비밀번호 초기화, 변경 이력 조회 |
| 파이프라인 상태 · AI 재분석 | 구성 요소 상태 확인, 처리 중인 고위험 이벤트 재분석 (분석가도 사용). AI 재분석은 AI 분석 서버가 연결된 배포에서만 보입니다 |

### 분석가 업무 흐름

| 기능 | 화면 | 설명 |
|---|---|---|
| 작업 큐 | 작업 큐 | 내가 맡은 미완료 건과 미할당 고위험 건을 위험 점수·대기 시간 순으로 표시. "내가 맡기"로 가져오면 접수 상태가 된다 |
| 처리 이력 | 이벤트 상세 | 상태 변경, 담당자 변경, 코멘트를 시간순으로 기록. 코멘트는 덮어쓰지 않고 쌓인다 |
| 일괄 처리 | 이상 거래 | 여러 이벤트를 선택해 같은 상태로 변경(최대 200건). 해결·오탐 종결은 사유 필수 |
| 예외 주소 제안 | 이벤트 상세 → 운영 설정 | 분석가가 오탐 지갑을 탐지 예외로 제안하고 관리자가 승인·반려·해제. 승인된 주소는 탐지에서 제외된다(제재 주소 탐지는 예외 불가) |
| 재오픈 | 이벤트 상세 | 종결한 이벤트를 다시 열면 종결 사유가 지워지고 이력에 재오픈으로 남는다. 신규로 되돌리면 담당자도 해제되어 미할당 큐로 돌아간다 |
| 거래 상대 | 지갑 상세 | 수집된 거래 기준으로 보낸 곳·받은 곳 상위 10개의 건수와 금액 합계 |

둘러보기 버튼은 `demo-viewer`, `demo-analyst` 계정의 세션을 비밀번호 없이 발급합니다. 두 계정은 일반 로그인으로는 들어올 수 없고, 관리자 데모는 제공하지 않습니다. 공개 환경에서 분석가 둘러보기를 켜 두면 방문자가 이벤트 상태를 바꿀 수 있습니다.

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

저장소에 배포 설정이 등록되어 있으면 같은 워크플로가 프런트엔드를 Cloudflare Workers에, 백엔드를 Render에 배포합니다. 설정 방법은 [배포 문서](deployment.md)를 참고하세요.

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
