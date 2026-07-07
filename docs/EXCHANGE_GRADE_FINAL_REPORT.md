# ChainWatch Exchange-Grade Improvement Sprint — 최종 보고서

작성일: 2026-07-08 · 기준 문서: [EXCHANGE_GRADE_CLAUDE_CODE_PROMPTS.md](EXCHANGE_GRADE_CLAUDE_CODE_PROMPTS.md) · 계획/진행: [EXCHANGE_GRADE_PLAN.md](EXCHANGE_GRADE_PLAN.md)

## 1. Executive Summary

포트폴리오 수준의 온체인 리스크 대시보드를 거래소 백오피스에 가까운 형태로 끌어올리는 스프린트를 3개 웨이브, 5개 역할 에이전트(백엔드/보안, AI, 인프라/SRE, 블록체인, 프론트엔드/UX)로 수행했다. 핵심 성과:

- **권한 분리와 감사 추적**: ADMIN/ANALYST 역할, 운영 액션 감사 로그, prod 프로필 fail-fast 가드
- **조사 워크플로**: 이벤트 담당자 배정·사유 필수 종결/오탐 처리, evidence-first 조사 화면
- **탐지 근거의 구조화**: 4개 룰 전부 "왜 발화했는지"를 JSON evidence + ruleVersion으로 기록
- **finality 개념 도입**: confirmation depth(기본 12) 기반 확정/미확정/판정불가 3상태
- **AI 신뢰성**: structured report 스키마, evidence-first 프롬프트, 프롬프트 인젝션 방어, 파싱 실패 시 우아한 강등
- **운영 준비**: 장애 런북 9종, AWS 배포 체크리스트, 실존 메트릭 기반 알림 규칙 3종 추가

전체 테스트: **백엔드 123/123, 프론트 56/56 + 빌드, AI 35/35, docker compose config 검증 통과.**

## 2. 영역별 변경 사항

### Backend
- ADMIN/ANALYST 역할 분리 (JWT 역할 클레임, 설정 기반 analyst 계정 `CHAINWATCH_ANALYST_*`)
- 컬렉터 제어·감사 로그 API는 ADMIN 전용, 나머지 `/api/**`는 ANALYST 이상. JSON 403 응답(`code: FORBIDDEN`)
- prod/staging 프로필 + `jwt-enabled=false` 조합은 기동 거부 (fail-fast)
- DetectionEvent 워크플로 필드: `assignee`, `statusChangedAt`, `resolutionReason`, `falsePositiveReason`, `notes` + `FALSE_POSITIVE` 상태. RESOLVED/FALSE_POSITIVE 전환 시 사유 필수(400)
- 신규 `audit` 패키지: 이벤트 상태 변경(동일 트랜잭션), 컬렉터 수동 수집, 로그인 성공/실패 기록. `GET /api/audit-logs` (ADMIN, 페이지 캡 100)
- `GET /api/events?status=` 필터, 음수 페이지 클램프

### Blockchain
- `confirmation-depth`(기본 12) + `last_known_chain_head` 관측 → 조회 시 `confirmations`/`confirmed` 계산 (컬럼 박제 없음, reorg rewind와 자동 정합, null=판정불가와 false 구분)
- 4개 탐지 룰(large-transfer, exchange-flow, rapid-transfer, watchlist-activity) 전부 구조화 evidence JSON + ruleVersion("1.0") 기록, 상세 API에 노출
- 멱등성 검토: txHash/(transaction_id,event_type) 유니크 + DLT 재시도 구조 건전 확인. 컬렉터의 예외 catch 범위 확대(제약 위반이 에러 메트릭 우회하던 갭 수정)
- [BLOCKCHAIN_ROADMAP.md](BLOCKCHAIN_ROADMAP.md) — finality/reorg 의미론, 입출금 추적·주소 라벨·hot/cold 태그 단기, non-EVM 장기 로드맵

### AI
- structured report 스키마: riskSummary, evidence[], possibleScenarios, recommendedActions, confidence, falsePositiveFactors, escalationLevel (기존 텍스트 report와 병행, 하위 호환)
- evidence-first 프롬프트: 제공된 근거만 사용, 이벤트 요약/지갑 라벨을 신뢰 불가 데이터로 구획, 인젝션 지시 무시 명시
- 파싱 실패/비정상 provider 응답 시 `structured=false` 텍스트 강등 (에러 대신)
- 신규 `report_parser.py` + 테스트. 백엔드 analysis DTO에 structured JSON 패스스루

### Frontend / UI·UX
- 이벤트 목록 → 밀도 높은 워크큐 테이블 (위험/점수/상태/담당자, FALSE_POSITIVE 포함 상태 필터)
- 이벤트 상세 → evidence-first 조사 페이지: 요약 스트립 → 룰 evidence(한국어 라벨 + 미지 키 폴백) → 트랜잭션 근거(확정 배지) → AI 보조 분석 → 운영 액션(사유 필수 검증, 실패 시 성공 표시 금지)
- AI structured report 렌더링 + 강등/실패/재시도 상태, "AI 보조 분석" 디스클레이머
- `#/admin/audit` 감사 로그 화면 (403 미인가 상태 처리)
- light-first 거래소 백오피스 리디자인 (흰 배경, 블루 프라이머리 #1663d9, 그라데이션 제거, 다크모드 보조 유지). 근거: [UX_REDESIGN_NOTES.md](UX_REDESIGN_NOTES.md)
- 공용 모듈: DataState(로딩/에러/빈/미인가), workflow 검증, aiReport/ruleEvidence 리졸버, ConfirmationBadge

### Infra / 운영
- [RUNBOOKS.md](RUNBOOKS.md): RPC/Kafka/Postgres/Redis/AI/웹훅 장애, DLT 재처리, 컬렉터 일시정지, reorg 급증 — 9종
- [DEPLOYMENT_CHECKLIST.md](DEPLOYMENT_CHECKLIST.md): 필수 env/시크릿 열거, TLS 전략, 마이그레이션 리스크, 롤백, AWS 권고안(ECS Fargate + RDS + ElastiCache + MSK + ALB/ACM)
- 알림 규칙 3종 추가 (DLT 발생, 컬렉터 수집 정지 프록시, reorg 급증) — 실존 메트릭만 사용, 부재 메트릭은 "요청 필요"로 명시
- 결함 수정: compose `profiles: ["app"]` 누락 회귀(30d32ff) 복원, 문서의 `/api/health`→`/actuator/health`, Kafka 토픽명 불일치

### Security (Red Team 관점)
- 관리 API 무인증 접근 차단 (역할 기반), 보호 검증 테스트 포함
- 프롬프트 인젝션 방어 (신뢰 불가 데이터 구획), AI 출력이 운영 액션을 직접 트리거하지 않음
- 감사 로그로 내부자 오용 추적 가능

## 3. 테스트 결과 (최종 재검증, 2026-07-08)

| 스위트 | 결과 |
|--------|------|
| 백엔드 `./gradlew test --rerun-tasks` | **123/123 통과** (스프린트 전 ~103 → +20) |
| 프론트 `npm test -- --run` | **56/56 통과** (11 → 56) |
| 프론트 `npm run build` | 통과 (tsc + vite) |
| AI `.venv pytest` | **35/35 통과** |
| `docker compose config --quiet` | 통과 |

## 4. 남은 리스크

**P1**
- `ddl-auto: update` + 마이그레이션 도구 부재 — 프로덕션 전 Flyway/Liquibase 도입 필수
- 로그인 브루트포스 방어 없음 (레이트 리밋/락아웃 미구현), 사용자 저장소가 설정 기반 2계정뿐

**P2**
- Kafka 발행이 DB 커밋 전 — 롤백 시 유령/중복 메시지 가능 (알림 dedup으로 완화, outbox 패턴 로드맵)
- DB/Redis/Kafka down·진짜 컬렉터 lag·SLA 미인지 이벤트 알림용 메트릭 부재 (exporter/게이지 추가 필요)
- AI 분석 서버에 Prometheus 메트릭 표면 없음
- 프론트가 역할을 선제 판별 못함 (`/api/auth/me` 부재, 403 반응형 처리만)
- 단일 브로커 Kafka (로컬 한정 허용)

**P3**
- 상태 전이 매트릭스 미적용 (RESOLVED→NEW 등 임의 전이 허용)
- 이벤트 상세에 per-event 감사 이력 없음 (audit-logs targetId 필터 필요)
- 고아 트랜잭션 마킹/정리 없음 (confirmed=false로 식별만 가능)

## 5. 채용 공고 정합성 (두나무/업비트·빗썸 계열)

충족: Spring Boot REST + 역할 기반 인가, Kafka 이벤트 아키텍처 + DLT + 멱등성, Redis dedupe, DB 제약/인덱스/정합성, reorg/finality 개념, 컴플라이언스 워크플로(조사/오탐/감사 로그), Prometheus/Grafana 관측성, Docker/compose, 런북/장애 대응 문서, AI 보조 판단(비권위적) 설계, OWASP 관점 하드닝.

미충족(실환경 필요): AWS 실 배포(계정/도메인), 실 LLM 키 연동 검증, 부하 테스트 실측, MSK/RDS 운영 경험 증빙.

## 6. 실 인프라/자격증명 필요 항목

- AWS 계정 + 도메인 + ACM 인증서 (배포 체크리스트에 절차 준비됨)
- Claude/Gemini API 키 (AI 실 provider 검증)
- Etherscan API 키 로테이션 (2026-07 보안 감사에서 이력 노출 확인된 건)

## 7. 다음 7일 로드맵

1. Flyway 도입 + `ddl-auto: validate` 전환 (P1)
2. 로그인 레이트 리밋 + `/api/auth/me` 엔드포인트 → 프론트 역할 선제 분기
3. 컬렉터 head-lag 게이지 + DB/Redis/Kafka 헬스 메트릭 브리지 → 보류된 알림 규칙 활성화
4. Kafka after-commit 발행(outbox) 전환
5. audit-logs targetId 필터 + 이벤트 상세 감사 이력 섹션
6. 상태 전이 매트릭스 적용
7. docker compose 풀스택 기동 스모크 테스트 (app 프로필 E2E)
