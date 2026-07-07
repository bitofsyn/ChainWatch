# ChainWatch Exchange-Grade Improvement Plan (Living Document)

기준 문서: [EXCHANGE_GRADE_CLAUDE_CODE_PROMPTS.md](EXCHANGE_GRADE_CLAUDE_CODE_PROMPTS.md)
시작일: 2026-07-07

## Phase 0 감사 결과 (베이스라인)

- 백엔드: `./gradlew test` 통과 (테스트 결과 20 클래스)
- 프론트엔드: vitest 11/11 통과, `npm run build` 성공
- AI 서버: pytest (venv `.venv`) — 실행 확인 중
- 기존 완료 자산: Kafka+DLT, Redis dedupe, reorg rewind, Prometheus/Grafana 프로비저닝, 이벤트 lifecycle 상태, 멀티페이지 SPA, non-root 컨테이너, 시크릿 기본값 차단

## 갭 분석 (문서 15장 최종 산출물 기준 대비)

| # | 항목 | 상태 | 우선순위 |
|---|------|------|---------|
| 1 | 보안 기본값 안전 백엔드 | 부분 (JWT off 시 /api/** 전체 개방, prod 가드 없음) | P1 |
| 2 | ADMIN/ANALYST 권한 분리 | 없음 | P1 |
| 3 | 운영 액션 감사 로그 | 없음 | P1 |
| 4 | DB 유니크 제약/인덱스 | 완료 | - |
| 5 | Kafka 재처리/DLT 전략 | 구현됨, 런북 문서 미비 | P2 |
| 6 | Redis dedupe | 완료 | - |
| 7 | 거래소형 UI/UX (light-first) | 미흡 | P2 |
| 8 | 이벤트 조사 workflow (assignee/사유) | 없음 (status만 존재) | P1 |
| 9 | reorg/finality 문서·테스트 | reorg 있음, finality(confirmation depth) 확인 필요 | P2 |
| 10 | AI structured report + 인젝션 방어 | 없음 (자유 텍스트) | P1 |
| 11 | Prometheus/Grafana | 완료, 알림 규칙 보강 필요 | P3 |
| 12 | 운영 runbook | 없음 | P2 |
| 13 | Docker compose 검증 | 완료, 재검증 필요 | P3 |
| 14 | AWS 배포 체크리스트 | OPERATIONS.md 일부, 전용 체크리스트 없음 | P2 |
| 15 | 최종 보고서 | 이번 스프린트 산출 | P1 |

## 실행 계획 (에이전트 웨이브)

### Wave 1 (병렬, 영역 분리)
- **Backend/Security Agent** (backend/): 권한 분리, 감사 로그, workflow 필드, prod 안전 가드, 보안 테스트
- **AI Agent** (ai/ + backend/analysis DTO): structured output 스키마, evidence-first 프롬프트, 인젝션 방어, 테스트
- **Infra/SRE Agent** (infra/, docs/, docker-compose.yml): 런북, AWS 배포 체크리스트, 알림 규칙, compose 검증

### Wave 2 (Wave 1 API 계약 확정 후)
- **Blockchain Agent** (backend/collector, detection): finality/confirmation, rule evidence, 멱등성 테스트, 로드맵
- **Frontend/UX Agent** (frontend/): 조사 워크플로 UI, 거래소형 light-first 리디자인, AI structured report 표시, 테스트+빌드

### Wave 3 (통합 검증)
- 전체 빌드/테스트 루프 (실패 시 수정 반복)
- QA 매트릭스, 최종 통합 보고서

## 완료 체크리스트

- [x] Wave 1 완료 + 백엔드 테스트 통과 (110개 그린 — 권한 분리/감사 로그/워크플로/AI DTO)
- [x] Wave 2 완료 + 프론트 테스트/빌드 통과 (블록체인 finality/evidence 123개 그린, 프론트 44개+빌드 그린)
- [x] AI 테스트 통과 (35개 그린)
- [x] 전체 통합 빌드 그린 (2026-07-08 최종 재검증: 백엔드 123/123 --rerun-tasks, 프론트 56/56+빌드, AI 35/35, compose config 통과)
- [x] 최종 보고서 ([EXCHANGE_GRADE_FINAL_REPORT.md](EXCHANGE_GRADE_FINAL_REPORT.md))

## 진행 로그

- 2026-07-07: Phase 0 감사, Wave 1 투입 (백엔드 보안/AI/인프라 3개 에이전트)
- 2026-07-08: Wave 1 완료 — AI 에이전트의 Java 람다 컴파일 오류 1건 직접 수정(AiAnalysisService storedStructuredReport). 인프라 에이전트가 compose profiles 누락 회귀(30d32ff) 복원. Wave 2 완료 — finality/rule evidence(+13 테스트), 프론트 워크큐/조사 페이지/light-first 리디자인(11→44 테스트). 프론트에 rule evidence 렌더링 후속 반영 중.
