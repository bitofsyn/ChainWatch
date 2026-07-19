# ChainWatch 운영 콘솔 UX 개편 노트 (Wave 1 프론트엔드)

작성: 2026-07-08, frontend+UI/UX agent. Wave1 백엔드/AI 계약(`WAVE1_BACKEND_CONTRACTS.md`, `WAVE1_AI_CONTRACT.md`) 반영.

## 1. 무엇을 왜 바꿨나

### 1.1 분석가 Workflow UI (P1)

- **이벤트 목록 → 작업 큐 테이블 전환**: 카드형 행을 촘촘한 `<table>`(#, 위험, 점수, 상태, 유형, 요약, 지갑, 담당자, 탐지 시각)로 교체. 컴플라이언스 담당자가 한 화면에서 20건을 스캔·선별하는 것이 목적이므로, 장식보다 밀도와 정렬을 우선했다.
- **상태 필터 추가**: `FALSE_POSITIVE` 포함 5단계 lifecycle 상태 필터를 필터 바에 추가 (`GET /api/events?status=`).
- **이벤트 상세 → 증거 우선(evidence-first) 조사 페이지**: 상단 요약 스트립(위험 점수/상태/유형/담당자/지갑/트랜잭션/탐지 시각) 아래에 룰 근거 → 트랜잭션 증거 → AI 보조 분석 → 운영 액션 순서로 배치. 조사자는 "무엇이, 왜 걸렸고, 증거가 무엇인지"를 먼저 보고 마지막에 판단(상태 변경)한다.
- **상태 변경 workflow**: PATCH 계약과 1:1 매칭. RESOLVED는 해결 사유, FALSE_POSITIVE는 오탐 사유가 필수이며 클라이언트 검증(`lib/workflow.ts`)이 백엔드 400 규칙을 선반영한다. 담당자(빈 값 = 해제)·운영 메모 편집 포함. 성공/실패 배너를 명확히 분리해 **API 실패 시 성공처럼 보이는 상태가 없다**.
- **AI 리포트 = 보조 근거**: 섹션 명칭을 "AI 보조 분석"으로 하고 "운영 판단을 대체하지 않는다"는 고지를 상시 노출. 구조화 리포트(riskSummary, 근거, 시나리오, 권장 조치, 오탐 요인, 신뢰도/에스컬레이션 배지)를 렌더링하고, `structured=false`면 텍스트 리포트로 폴백하며 degraded 안내를 표시한다. 근거(evidence)가 비면 "근거 데이터 없음" 경고를 명시. `FAILED`는 재시도 버튼 제공.
- **감사 로그**: 관리자 콘솔에 "감사 로그" 섹션 신설 (`/admin/audit`). 수행자/액션 필터 + 페이지네이션 테이블. 403/401은 "ADMIN 권한 필요" 권한없음 상태로 안내한다.

### 1.2 거래소 백오피스 스타일 재설계 (라이트 우선)

업비트/빗썸 계열 운영 도구의 공통 문법을 따랐다.

- **라이트 모드 기본**: 밝은 중성 배경(#f3f5f8) + 흰 패널. 시스템 다크 선호가 명시된 경우에만 다크로 시작하고, 다크 모드는 보조로 유지(토큰 전면 재정의).
- **블루 프라이머리**: 신뢰 색상인 블루(#1663d9)를 주요 액션·활성 상태·링크에 사용. 기존 시안(cyan) 계열과 그라디언트 장식(배경, 바 차트, 브랜드 마크)을 제거하고 단색으로 교체.
- **밀도와 radius 축소**: 카드 12px→8px, 배지 6px→4px, 패딩·행 높이 축소. 본문 기본 14px. 숫자는 `tabular-nums`로 정렬해 점수·건수의 숫자 위계를 강화.
- **표 중심 정보 구조**: 작업 큐·감사 로그는 실제 `<table>`, 보더는 행 하단 1px으로 절제. hover는 배경만 미세하게.
- **중첩 카드 지양**: 상세 페이지 내부 블록은 카드-안-카드 대신 얕은 surface 톤 + 1px 보더로 층위를 표현.
- **접근성**: 모든 폼 요소 label/aria-label 유지, `:focus-visible` 링 추가, 상태 배지는 색+텍스트 병기, 필수 사유 필드에 "(필수)" 텍스트 명시. 색 대비는 라이트/다크 모두 텍스트 대비 기준으로 토큰을 조정했다.
- **테마 일관성**: 배지/배너 배경을 하드코딩 rgba 대신 `color-mix(... var(--색) N%, transparent)`로 통일해 라이트/다크 모두에서 동일한 규칙으로 렌더링.

### 1.3 신뢰성/중복 제거

- `components/DataState.tsx`: 로딩/오류(재시도)/빈/권한없음 상태 공용 렌더러. 이벤트 큐, 상세, AI 섹션, 감사 로그에 적용.
- `lib/workflow.ts`: 상태 변경 검증 + PATCH body 생성 (테스트 가능한 순수 함수).
- `lib/aiReport.ts`: AI 리포트 렌더 분기(none/pending/failed/structured/text/empty) 순수 함수 + 신뢰도/에스컬레이션 라벨.
- `lib/format.ts`: `formatFullDate`(연도 포함), `formatAmount`(금액+단위) 추가로 산발적 포맷팅 중복 제거.

### 1.4 Wave2 블록체인 계약 반영 (룰 evidence / 확정 깊이)

- **룰 evidence 렌더링**: 이벤트 상세 룰 근거 섹션에 `evidence` JSON을 렌더링한다(`lib/ruleEvidence.ts`, 순수 함수 + 테스트). 4개 룰(large-transfer/exchange-flow/rapid-transfer/watchlist-activity)의 알려진 키는 한국어 라벨 + 단위 포맷(ETH, 분, 회, 시각), 방향값(INBOUND/OUTBOUND, FROM/TO)은 의미 라벨 병기. 계약에 없는 키는 raw key/value로 방어적으로 나열하고, evidence 부재/null/비객체면 기존 "근거 없음" 상태로 표시. `ruleVersion`은 탐지 규칙 옆 배지로 표기.
- **트랜잭션 확정 배지**: `confirmations`/`confirmed` 필드를 3단계(확정/미확정/판정 불가) 배지(`components/ConfirmationBadge.tsx`)로 이벤트 상세·트랜잭션 상세에 노출. `null`(head 미관측)은 계약대로 미확정(false)과 구분해 "판정 불가"로 처리하고, 미확정은 reorg 되감기 가능 구간임을 보조 텍스트로 안내.

## 2. 남은 UX/API 갭

- **역할 인지 부재**: 프론트가 JWT의 roles claim을 파싱하지 않아 ANALYST/ADMIN UI를 사전에 구분하지 못한다. 지금은 토큰 유무로만 액션을 노출하고 403을 사후 처리한다. → 백엔드가 `/api/auth/me` 또는 토큰 claim 공개 계약을 주면 개선 가능.
- **감사 로그 진입 동선**: 관리자 콘솔 하위에만 있다. 이벤트 상세에서 해당 이벤트의 액션 이력(타깃 필터)을 바로 보여주는 API 파라미터(`targetId=`)가 생기면 상세 페이지에 "처리 이력" 섹션을 붙일 수 있다.
- **비동기 분석 폴링 없음**: `POST /analysis`(동기)만 사용. PENDING 상태 자동 갱신(폴링/SSE)은 미구현 — 수동 새로고침 필요.
- **오버뷰 운영 지표**: collector lag, Kafka/DLT, AI 큐 등 파이프라인 지표가 오버뷰 첫 화면에 아직 없다(관리자 파이프라인 탭에만 존재). ops 지표 API가 공개 범위로 정리되면 오버뷰 상단에 편입할 가치가 있다.
- **컴포넌트 렌더 테스트 부재**: jsdom/@testing-library 미도입 정책으로 로직(순수 함수) 테스트만 확장했다. 렌더 수준 회귀 방지가 필요해지면 도입 논의 필요.

## 3. 실시간 모션·시계열 UX 고도화 (2026-07-18)

`docs/REALTIME_MOTION_UX_CLAUDE_CODE_PROMPT.md` 기반 구현. 외부 라이브러리 추가 없음(SVG + rAF).

### 3.1 공용 Motion System

- `styles.css` `:root`에 motion token 정의: `--motion-instant/fast/base/slow`(80/140/220/360ms), `--ease-standard/emphasized/exit`. 신규 모션은 전부 이 토큰만 사용.
- `hooks/usePrefersReducedMotion.ts`: JS 기반 애니메이션(count-up, 시리즈 morph, ripple, 진입 클래스)도 사용자 모션 설정을 따르도록 논리적으로 비활성화. CSS 쪽은 기존 전역 reduce 블록 + 신규 애니메이션 `animation: none` 명시.

### 3.2 실시간 상태 모델

- `lib/liveStatus.ts`(순수 함수): LIVE / UPDATING(복귀 동기화 구분) / STALE(부분 실패 또는 90초 초과) / PAUSED(탭 비활성) / OFFLINE(전체 실패) 판정. `navigator.onLine`은 OFFLINE 상태의 보조 힌트로만 사용.
- `components/LiveStatusCluster.tsx`: 상태 점+텍스트+마지막 성공 시각, countdown은 독립 컴포넌트로 격리(매초 전체 rerender 금지). LIVE 점은 상시 pulse하지 않고 데이터 도착 순간 1회 ripple. aria-live는 상태 종류 변경 시에만 갱신.
- `useOverviewData`: setInterval → setTimeout 체인으로 변경해 `nextRefreshAt`이 항상 실제 예정 시각(수동 새로고침 후에도 정직한 countdown).

### 3.3 데이터 diff 모델

- `lib/overviewDiff.ts`(순수 함수 + 테스트): `ValueChange`(previous/current/delta/deltaPercent/direction/changedAt). 최초 로드·null 전환은 변화로 취급하지 않음. range 변경은 query transition으로 diff 생략(overview 응답의 range/bucket 에코 비교).
- KPI별 semantic direction: lag/탐지율/backlog = higher-worse(상승 주의), 처리량 = neutral.
- 신규 항목: 이전 성공 응답의 `event.id`/`txHash` 집합과 비교(`findNewKeys`), 시계열 신규 bucket은 `newBucketKeys`.

### 3.4 시계열 차트 계약·가정 (문서화된 추측)

- **partial bucket**: 백엔드 계약에 명시했다(2026-07-19) — `OpsOverviewResponse.SeriesPoint.partial`(버킷 종료 시각 > 집계 시각). 프론트는 `bucketPartial()`이 서버 값을 우선하고, 필드가 없는 구버전 응답만 `bucketStart + bucket길이 > 서버 generatedAt`으로 폴백 판별한다.
- **null vs 0**: `detectionRatePercent == null`(수집 0건)은 선을 잇지 않고 gap(`gappedLinePath`). 수집/탐지 count는 계약상 non-null이라 0은 측정된 0으로 그린다.
- polling 갱신은 bucket key 기준 값 보간 morph(320ms, `useSeriesTransition`) — 문자열 path 보간 금지. bucket 집합이 겹치지 않으면 즉시 교체. range 변경은 plot `<g key={queryKey}>` remount + 180ms crossfade.
- anomaly marker는 `throughputInsight`와 동일 결과 객체를 공유(단일 source), 유형별 모양 상이, 최초 발견 1회만 진입 애니메이션.
- 키보드: 차트 전체가 단일 focusable composite(수십 개 tabbable rect 제거). ←/→/Home/End 이동, Esc/외부 클릭 해제, 선택은 bucket key로 저장해 시계열 이동에도 유지. 스크린리더용 숨김 데이터 테이블 제공.

### 3.5 한계·후속

- 컴포넌트 렌더 테스트: 기존 jsdom/@testing-library 미도입 정책 유지 — 상태·판정 로직을 순수 함수로 내려 unit 테스트로 대체(diff 27, geometry 26, liveStatus 9 등 114 tests).
- 조사 큐 재정렬 배치 반영("새 이벤트 N건" 버튼)은 큐가 최대 8행이라 미도입. 행 수가 늘면 재검토.
- reduced-motion 브라우저 에뮬레이션 검증은 수동 필요(로직 분기는 unit 테스트로 검증).
- SSE/WebSocket 미도입 — 30초 polling을 UI에 명시(프로토콜 변경은 별도 단계).
