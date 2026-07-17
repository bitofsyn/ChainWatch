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
- [x] AI 실 LLM(Claude/Gemini) 연동 검증 — Claude 기본 + Gemini 폴백, mock 제외 정직한 실패, 인젝션 스모크 통과
- [x] 백엔드 `chainwatch.ai.enabled=true` end-to-end 연동 테스트 — 재분석 UI→이벤트 상세 구조화 리포트 렌더 확인
- [x] `AiAnalysisService`의 동기 `.block()` 호출 비동기화 검토 (전용 executor 기반 `POST /analysis/async` 경로 추가, 동기 API는 하위호환 유지)

## Phase 9 — 장기 기능 확장 (로드맵 3번)
### 분석가 워크플로 UI
- [x] 담당자 큐 필터 — API `assignee`(대소문자 무시)/`unassigned` + 작업 큐 퀵필터(전체/내 케이스/미할당) + '나에게 할당'
- [ ] 케이스 에스컬레이션 — 등급 상향/재배정 전용 액션과 담당자 변경 이력 타임라인
- [ ] 담당자별 처리 SLA·부하 대시보드 (미처리 건수/평균 처리시간)
- [ ] 상태 전이 감사 이력을 이벤트 상세 타임라인으로 노출 (audit_logs의 EVENT_STATUS_CHANGE 활용)

### 멀티체인 지원
- [x] `network` 차원 도입 — Transaction/DetectionEvent에 network 컬럼(하위호환), API 노출·필터, 프론트 체인 배지·필터
- [ ] 수집기 멀티체인화 — EVM 체인(Polygon/Arbitrum)용 병렬 수집기 인스턴스, 체인별 RPC/키 config
- [ ] 비-EVM 체인(Solana 등) 별도 수집기 어댑터
- 선결 과제: 체인별 RPC/키 관리, 탐지 룰의 체인 독립성 검토

### 탐지 로직 고도화
- [x] 그래프 분석 1차 — FAN_OUT 룰(트랜잭션 그래프 out-degree 기반 자금 분산/peeling chain 탐지), evidence에 관측 out-degree 기록
- [ ] 그래프 분석 확장 — fan-in(consolidation), multi-hop 흐름 추적, 믹서 클러스터 탐지
- [ ] ML 이상탐지 — 룰 기반 점수에 학습 모델 보강(라벨 데이터·피처 파이프라인·오프라인 학습 인프라 필요)
- 선결 과제: 학습 데이터 확보 전략, 룰 엔진과 ML 점수 결합 방식
