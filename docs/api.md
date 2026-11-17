# 주요 API

| 영역 | 경로 | 설명 |
|---|---|---|
| 상태 확인 | `GET /api/health` | 백엔드 상태 확인 |
| 이벤트 | `GET /api/events` | 탐지 이벤트 검색 및 페이지 조회 |
| 이벤트 | `GET /api/events/{id}` | 이벤트와 탐지 근거 상세 조회 |
| 이벤트 | `PATCH /api/events/{id}/status` | 이벤트 처리 상태 변경 |
| AI 분석 | `POST /api/events/{id}/analysis` | 이벤트 AI 분석 실행 |
| 거래 | `GET /api/transactions` | 수집된 거래 조회 |
| 지갑 | `GET /api/wallets/{address}` | 지갑 활동 요약 조회 |
| 탐지 규칙 | `GET /api/detection/rules` | 규칙 및 임계값 조회 |
| 탐지 규칙 | `PATCH /api/detection/rules/thresholds` | 임계값 변경 |
| 수집기 | `GET /api/collector/state` | 현재 수집 상태 조회 |
| 운영 | `GET /api/ops/pipeline` | 파이프라인 상태 조회 |
| 운영 설정 | `GET /api/admin/operational-settings` | 수집·웹훅 설정 조회(ADMIN) |
| 운영 설정 | `PATCH /api/admin/operational-settings` | 수집·웹훅 설정 변경(ADMIN) |
| 인증 | `POST /api/auth/login` | 로그인 및 토큰 발급 |

전체 명세는 서비스를 실행한 뒤 Swagger UI에서 확인할 수 있습니다.
