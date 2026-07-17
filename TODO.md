# TODO

Phase 기준 남은 작업. 완료 항목은 [CHANGELOG.md](CHANGELOG.md)로 이동.

## Phase 5 잔여 (API Server)
- [ ] Collector 리팩토링(진행 중) 완료 후 통합 빌드 재검증
- [ ] Transaction/Event 조회 API 응답 구조 고도화
- [x] 인덱스 전략 설계 (`tx_hash`, `from_address`, `to_address`, `block_number`, `timestamp` + detection_events 인덱스/유니크 제약)
- [x] DB 기반 사용자/리프레시 토큰 (user_accounts/refresh_tokens, 회전식 리프레시, ADMIN 발급 계정, /api/users 관리 API)

## Phase 6 — Frontend
- [x] JWT 로그인 화면 및 토큰 저장/갱신 로직 (#/login, AuthContext, 401 시 단일비행 refresh 재시도, 사용자 관리 UI)
- [x] 이벤트 유형 분포 차트 (CSS 바 차트)
- [x] 시간대별 탐지 추이 차트 (`/api/events/stats/trend` + Overview TrendChart)
- [x] 검색/필터 UI (지갑 주소, 유형, 위험 등급)
- [x] 기간(from/to) 필터 UI (이벤트 목록 datetime-local 필터)
- [x] Dark Mode 토글 + CSS 변수 테마
- [x] 반응형 레이아웃 (기존 + 필터 바 대응)

## Phase 7 — Notification
- [x] Slack / Discord Webhook 연동 (URL 미설정 시 자동 비활성)
- [x] 위험도 기준 알림 정책(min-risk-score), TTL 기반 중복 알림 방지
- [x] 알림 이력 DB 저장 (`notification_history` + `/api/notifications/history`)
- [x] Redis 기반 데듀플리케이터 (다중 인스턴스 대응, `dedup-store: redis|memory`, 장애 시 fail-open)

## Phase 8 — 운영 환경
- [x] AI 분석 서버 Dockerfile + docker-compose 통합
- [x] GitHub Actions: AI 분석 서버 CI (pytest)
- [x] GitHub Actions: 백엔드 CI (Gradle build+test), 프론트엔드 CI (vitest+build)
- [x] 백엔드(멀티스테이지 Gradle)/프론트엔드(Nginx) Dockerfile + compose `app` 프로필
- [x] Nginx 리버스 프록시 (frontend 이미지 내 /api 프록시)
- [x] Prometheus + Grafana compose 구성 (데이터소스 자동 프로비저닝)
- [x] logback 로그 포맷 통일
- [x] 운영 가이드 문서 (docs/OPERATIONS.md — AWS EC2 배포 절차 포함)
- [ ] AWS 실 배포 및 HTTPS(ALB/ACM) 구성 — 계정/도메인 필요
- [x] Grafana 대시보드 JSON 프로비저닝(chainwatch-overview), Prometheus 알림 규칙(prometheus-rules.yml)

## 기타
- [ ] AI 실 LLM(Claude/Gemini) 연동 검증 (API 키 필요)
- [ ] 백엔드 `chainwatch.ai.enabled=true` end-to-end 연동 테스트
- [x] `AiAnalysisService`의 동기 `.block()` 호출 비동기화 검토 (전용 executor 기반 `POST /analysis/async` 경로 추가, 동기 API는 하위호환 유지)
