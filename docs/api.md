# 주요 API

권한은 JWT 인증을 켠 환경(`CHAINWATCH_JWT_ENABLED=true`) 기준입니다. JWT를 끈 로컬 개발 환경에서는 권한을 검사하지 않습니다. 요청·응답 전체 명세는 서비스를 실행한 뒤 Swagger UI(`/swagger-ui.html`)에서 확인할 수 있습니다.

| 영역 | 메서드·경로 | 설명 | 권한 |
|---|---|---|---|
| 상태 | `GET /api/health` | 백엔드 상태와 실행 중인 커밋 | 공개 |
| 인증 | `POST /api/auth/login` | 로그인, 토큰 발급 | 공개 |
| 인증 | `POST /api/auth/demo` | 둘러보기 세션 발급(조회자·분석가) | 공개 |
| 인증 | `POST /api/auth/refresh` | 토큰 재발급 | 공개 |
| 인증 | `GET /api/auth/me` · `POST /api/auth/logout` · `PATCH /api/auth/password` | 내 정보, 로그아웃, 비밀번호 변경 | 로그인 |
| 이벤트 | `GET /api/events` | 탐지 이벤트 검색·페이지 조회 | 조회자 이상 |
| 이벤트 | `GET /api/events/{id}` | 이벤트와 탐지 근거 상세 | 조회자 이상 |
| 이벤트 | `GET /api/events/stats` · `GET /api/events/stats/trend` | 위험도·유형·상태별 통계(10초 캐시), 시간대별 추이(최대 168시간) | 조회자 이상 |
| 이벤트 | `GET /api/events/{id}/activities` | 처리 이력 | 조회자 이상 |
| 이벤트 | `PATCH /api/events/{id}/status` | 상태·담당자·종결 사유 변경 | 분석가·관리자 |
| 이벤트 | `POST /api/events/{id}/comments` | 코멘트 추가 | 분석가·관리자 |
| 이벤트 | `POST /api/events/bulk-status` | 여러 이벤트 상태 일괄 변경(최대 200건) | 분석가·관리자 |
| AI 분석 | `POST /api/events/{id}/analysis` · `POST /api/events/{id}/analysis/async` | AI 분석 실행(동기·비동기) | 분석가·관리자 |
| 작업 큐 | `GET /api/workqueue` | 내 미완료 건과 미할당 고위험 건 | 분석가·관리자 |
| 거래 | `GET /api/transactions` | 수집된 거래 조회. 전체 건수 없이 다음 페이지 여부(`last`)만 반환 | 조회자 이상 |
| 거래 | `GET /api/transactions/{id}` | 거래 상세 | 조회자 이상 |
| 지갑 | `GET /api/wallets/{address}` | 지갑 활동 요약 | 조회자 이상 |
| 지갑 | `GET /api/wallets/{address}/counterparties` | 거래 상대 상위 집계(기본 10개) | 조회자 이상 |
| 피드 | `GET /api/feed/recent-events` · `GET /api/feed/recent-transactions` | 최신 이벤트·거래 피드 | 조회자 이상 |
| 운영 | `GET /api/ops/overview` | 대시보드 지표(수집 지연, 처리량, 탐지율, 대응 대기 건수)와 시계열 | 조회자 이상 |
| 운영 | `GET /api/ops/pipeline` | 구성 요소별 파이프라인 상태 | 조회자 이상 |
| 탐지 규칙 | `GET /api/detection/rules` | 규칙과 임계값 | 조회자 이상 |
| 탐지 규칙 | `PATCH /api/detection/rules/thresholds` | 임계값 변경 | 관리자 |
| 예외 주소 | `GET /api/policy-suggestions` · `POST /api/policy-suggestions` | 탐지 예외 주소 제안 조회·등록 | 분석가·관리자 |
| 예외 주소 | `PATCH /api/admin/policy-suggestions/{id}` | 제안 승인·반려·해제 | 관리자 |
| 수집기 | `GET /api/collector/state` | 마지막 수집 블록 | 분석가·관리자 |
| 수집기 | `POST /api/collector/blocks/latest` · `POST /api/collector/blocks/{blockNumber}` | 최신 블록·지정 블록 수동 수집 | 관리자 |
| 운영 설정 | `GET /api/admin/operational-settings` · `PATCH /api/admin/operational-settings` | 자동 수집, 알림, 최소 위험 점수, 웹훅 설정 | 관리자 |
| 알림 | `GET /api/notifications/history` | 알림 전송 이력 | 분석가·관리자 |
| 제재 주소 | `GET /api/sanctions` | OFAC 제재 주소 동기화 상태 | 분석가·관리자 |
| 제재 주소 | `POST /api/sanctions/sync` | 제재 주소 수동 동기화 | 관리자 |
| 사용자 | `GET /api/users` · `POST /api/users` · `PATCH /api/users/{id}` · `POST /api/users/{id}/password-reset` | 계정 조회·생성·역할 변경·비밀번호 초기화 | 관리자 |
| 감사 로그 | `GET /api/audit-logs` | 관리 작업 변경 이력 | 관리자 |
