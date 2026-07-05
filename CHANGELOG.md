# Changelog

ChainWatch 주요 변경 이력. 형식: Phase 단위 + 날짜.

## 2026-07-05

### Phase 7 — Notification (`b144d5f`)
- Slack/Discord Webhook 알림 채널, 위험도 임계 정책, TTL 중복 방지
- Kafka detected-events 기반 별도 컨슈머 그룹으로 발송

### Phase 6 — Frontend (`45fd7ec`)
- 검색/필터 바(유형/등급/지갑), 이벤트 유형 분포 CSS 차트
- 다크/라이트 테마 토글(localStorage), CSS 변수 테마 체계, vitest 도입

### 문서/인프라 (`e41e88f`)
- 루트 README/CHANGELOG/TODO, AI 서버 Dockerfile + compose 통합, AI 서버 CI

### Phase 5 — API Server 보안 (`edecc8d`)
- Spring Security JWT 인증 추가 (`chainwatch.security.jwt-enabled` 플래그, 기본 비활성으로 호환성 유지)
- `POST /api/auth/login` 로그인 API 및 Bearer 토큰 발급
- springdoc-openapi Swagger UI(`/swagger-ui.html`) + bearerAuth 스키마
- 인증/검증 예외를 공통 `ApiErrorResponse` 포맷으로 통합

### Phase 4 — AI Analysis Server (`9f9fbb4`)
- `ai/analysis-server` FastAPI 서버 신규 구현
- Model Adapter 패턴: Claude(anthropic SDK) / Gemini / LM Studio·Hermes(OpenAI 호환) / Mock
- 프로바이더별 Retry + Fallback 체인, 프롬프트 템플릿 버전 관리(v1)
- pytest 14건 + 스모크 테스트

## 이전 (Phase 1~3)

- `4f7bbdf` Kafka Consumer 설정 컴파일 오류 수정
- `2c79cf2` 프론트엔드-백엔드 API 연동
- `9003ccb` 프론트엔드 대시보드 초기 구성
- `7905502` AI 분석 연계 골격 추가
- `a39c9a9` Kafka Consumer 및 Redis 피드 캐시 추가
- `6ab1ad7` Kafka 연동 및 로컬 인프라 구성 (Docker Compose: PostgreSQL/Redis/Kafka)
- `282f903` 이상거래 탐지 규칙 확장 (고액/반복/거래소/워치리스트)
- `3ef71d3` 백엔드 구조 정리 및 Etherscan 탐지 흐름 추가
- `acf6423` Collector 안정화 및 수집 상태 추적
- `b4cf238` 이상거래 이벤트 도메인 및 조회 API
- `afc7811` 백엔드 초기 골격 구성
