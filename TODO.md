# TODO

Phase 기준 남은 작업. 완료 항목은 [CHANGELOG.md](CHANGELOG.md)로 이동.

## Phase 5 잔여 (API Server)
- [ ] Collector 리팩토링(진행 중) 완료 후 통합 빌드 재검증
- [ ] Transaction/Event 조회 API 응답 구조 고도화
- [ ] 인덱스 전략 설계 (`tx_hash`, `from_address`, `to_address`, `block_number`, `timestamp`)
- [ ] DB 기반 사용자/리프레시 토큰 (현재 설정 기반 단일 관리자)

## Phase 6 — Frontend
- [ ] JWT 로그인 화면 및 토큰 저장/갱신 로직 (jwt-enabled 시)
- [ ] 차트 기반 시각화 (위험도 분포, 시간대별 탐지 추이)
- [ ] 검색/필터 UI 고도화 (지갑 주소, 기간, 위험도)
- [ ] Dark Mode, 반응형 레이아웃, UI 통일

## Phase 7 — Notification
- [ ] Slack / Discord Webhook 연동
- [ ] 위험도 기준 알림 정책, 중복 알림 방지
- [ ] 알림 이력 저장

## Phase 8 — 운영 환경
- [x] AI 분석 서버 Dockerfile + docker-compose 통합
- [x] GitHub Actions: AI 분석 서버 CI (pytest)
- [ ] GitHub Actions: 백엔드 CI (Gradle build) — Collector 리팩토링 완료 후
- [ ] 백엔드/프론트엔드 Dockerfile
- [ ] Nginx 리버스 프록시, AWS 배포
- [ ] Prometheus 메트릭 + Grafana 대시보드
- [ ] 구조화 로깅 및 로그 수집

## 기타
- [ ] AI 실 LLM(Claude/Gemini) 연동 검증 (API 키 필요)
- [ ] 백엔드 `chainwatch.ai.enabled=true` end-to-end 연동 테스트
- [ ] `AiAnalysisService`의 동기 `.block()` 호출 비동기화 검토
