# ChainWatch 실시간 모션·시계열 UX 고도화 — Claude Code 프롬프트

아래 `Prompt` 전체를 Claude Code에 붙여 넣는다.

---

## Prompt

너는 금융·거래소·관제 대시보드를 다수 설계하고 운영한 **10년차 Senior Frontend Engineer 겸 Senior Product/UI·UX Designer**다. 현재 ChainWatch 저장소를 먼저 분석한 뒤, 실시간 데이터 변화가 운영자에게 빠르고 정확하게 인지되는 UI/UX와 실무 수준의 파이프라인 처리량 차트를 실제로 구현하라. 계획이나 시안만 작성하지 말고 코드 수정, 테스트, 빌드, 실제 브라우저 검증까지 완료하라.

이 제품은 소비자용 코인 시세 앱이 아니라 빗썸·업비트(두나무) 환경을 지향하는 **온체인 이상거래 및 데이터 파이프라인 운영 콘솔**이다. 화려함보다 상황 인지, 변화 추적, 장애 판단, 데이터 신뢰성을 우선한다.

## 1. 먼저 읽을 파일

작업 전에 아래 파일과 관련 테스트·타입·API 계약을 읽어 기존 동작을 보존하라.

- `frontend/src/pages/OverviewPage.tsx`
- `frontend/src/components/TimeSeriesChart.tsx`
- `frontend/src/components/MetricCard.tsx`
- `frontend/src/components/PipelineHealthStrip.tsx`
- `frontend/src/components/RiskStatusMatrix.tsx`
- `frontend/src/components/DistributionChart.tsx`
- `frontend/src/hooks/useOverviewData.ts`
- `frontend/src/lib/opsOverview.ts`
- `frontend/src/lib/opsOverview.test.ts`
- `frontend/src/types.ts`
- `frontend/src/api.ts`
- `frontend/src/styles.css`
- `backend/src/main/java/com/chainwatch/backend/ops/`
- `docs/UX_REDESIGN_NOTES.md`

현재 구현을 다음처럼 이해하고 출발하라.

- Overview는 30초 polling, 탭 비활성화 중단, AbortController, widget별 partial failure와 stale data 보존을 이미 지원한다.
- `TimeSeriesChart`는 SVG로 수집 트랜잭션 막대, 탐지 이벤트 실선, 탐지율 점선을 표시하고 hover/focus tooltip을 지원한다.
- 현재 데이터 갱신은 시각적으로 즉시 교체되어 **무엇이 얼마나 변했는지** 인지하기 어렵다.
- 현재 “갱신 중”은 텍스트뿐이며 연결 상태, 다음 갱신까지 남은 시간, 새 데이터 도착, stale 정도가 충분히 구분되지 않는다.
- 실시간처럼 보이기 위한 가짜 숫자나 무한 애니메이션을 추가하면 안 된다.

## 2. 디자인 원칙

모든 모션은 아래 네 가지 목적 중 하나를 가져야 한다.

1. **Continuity**: 이전 데이터에서 새 데이터로 어떻게 변했는지 연결한다.
2. **Attention**: 운영자가 확인해야 할 신규·급증·장애만 제한적으로 강조한다.
3. **Feedback**: 새로고침, 범위 변경, 선택, hover/focus의 결과를 알려준다.
4. **Orientation**: 차트와 표에서 현재 위치와 시간 흐름을 잃지 않게 한다.

목적 없는 bounce, glow, parallax, 끊임없는 pulse, 숫자를 실제보다 역동적으로 보이게 하는 연출은 금지한다. 금융 관제 화면답게 침착하고 정밀해야 한다.

## 3. 공용 Motion System 구축

`styles.css`에 흩어진 transition을 더 늘리지 말고 공용 motion token을 정의하라.

권장 토큰:

```css
--motion-instant: 80ms;
--motion-fast: 140ms;
--motion-base: 220ms;
--motion-slow: 360ms;
--ease-standard: cubic-bezier(.2, 0, 0, 1);
--ease-emphasized: cubic-bezier(.2, .8, .2, 1);
--ease-exit: cubic-bezier(.4, 0, 1, 1);
```

- 색/opacity는 140~220ms, 위치/크기는 220~360ms 범위로 제한한다.
- 숫자 갱신, 표 행 진입, 상태 변경, tooltip, 차트 전환에 같은 모션 언어를 적용한다.
- transform과 opacity 중심으로 구현해 layout thrashing을 피한다.
- `prefers-reduced-motion: reduce`에서는 duration만 0.01ms로 속이는 수준을 넘어, 숫자 count-up, path draw, pulse, 자동 이동을 논리적으로 비활성화하고 즉시 최종 상태를 보여준다.
- 공용 hook `usePrefersReducedMotion()` 또는 동등한 구조를 만들어 JS 기반 애니메이션도 사용자 설정을 따른다.

## 4. 실시간 상태 표시 UX

Overview 헤더의 갱신 UX를 다음과 같이 고도화하라.

### 4.1 Live Status Cluster

다음 상태를 명확히 구분한다.

- `LIVE`: 최근 갱신 성공, 다음 polling 예정
- `UPDATING`: background refresh 진행 중, 기존 데이터 유지
- `STALE`: 최근 갱신 실패 또는 마지막 성공 후 기준 시간을 초과
- `PAUSED`: 브라우저 탭 비활성화로 polling 중단
- `OFFLINE`: 모든 API 실패 또는 네트워크 연결 없음

구현 요구:

- 작은 상태 점 + 텍스트 + 마지막 성공 시각을 표시하되 색만으로 구분하지 않는다.
- LIVE 상태 점은 계속 pulse하지 않는다. 데이터가 실제로 성공적으로 도착한 순간에만 1회 600~900ms 정도의 restrained ripple을 실행한다.
- 다음 자동 갱신까지 `29초 후 갱신` 형태의 countdown을 제공하되 매초 React 전체 대시보드를 rerender하지 않도록 독립 컴포넌트 또는 작은 state boundary로 격리한다.
- 수동 새로고침 버튼 아이콘은 요청 진행 중에만 회전하며, 텍스트 레이블과 `aria-live` 피드백을 제공한다.
- 탭이 다시 활성화되면 즉시 조회하되 “복귀 후 동기화 중” 상태를 잠시 명확히 표시한다.
- `navigator.onLine`만으로 서버 상태를 확정하지 말고 네트워크 힌트로만 사용한다.

### 4.2 데이터 신선도

- 각 주요 위젯에 필요하면 `방금 전 / 32초 전 / 2분 전` freshness를 표시한다.
- stale data는 opacity를 지나치게 낮추지 말고, 상단에 명확한 stale badge와 마지막 성공 시간을 제공한다.
- 갱신 실패 시 기존 차트가 사라지거나 skeleton으로 되돌아가지 않게 한다.
- stale와 empty를 절대 같은 화면으로 표현하지 않는다.

## 5. 데이터 변화 감지 모델

애니메이션을 CSS에만 추가하지 말고 이전 성공 응답과 새 성공 응답을 비교하는 명시적인 diff 모델을 만든다.

예시:

```ts
type ChangeDirection = "up" | "down" | "same" | "initial";

interface ValueChange {
  previous: number | null;
  current: number | null;
  delta: number | null;
  deltaPercent: number | null;
  direction: ChangeDirection;
  changedAt: number;
}
```

- `useOverviewData`가 이전 데이터를 무조건 버리지 않도록 `previousOverview` 또는 계산된 change metadata를 안전하게 제공한다.
- 최초 로드는 변화로 취급하지 않는다.
- 실패 응답/stale 유지 상태에서는 변화 애니메이션을 실행하지 않는다.
- range 변경으로 bucket 집합이 바뀐 경우 실제 실시간 변화와 구분한다. 이를 “query transition”으로 처리하고 신규 데이터 경보로 표시하지 않는다.
- 객체/배열 전체를 깊게 비교하지 말고 필요한 KPI와 bucket key를 기준으로 효율적으로 계산한다.
- diff 계산은 순수 함수로 분리하고 Vitest 테스트를 작성한다.

## 6. KPI 변화 애니메이션

Collector Lag, 처리량, 탐지율, 대응 Backlog에 다음을 적용한다.

- 값이 바뀌면 이전 값에서 새 값까지 300~500ms count transition을 적용한다.
- 큰 수는 모든 중간 정수를 DOM에 렌더링하지 말고 `requestAnimationFrame`과 easing으로 표현한다.
- 단위, 소수점 정밀도, null(`—`) 정책을 보존한다.
- 변경된 카드에 600~900ms 동안 얇은 border tint 또는 surface flash를 1회 적용한다.
- 상승이 항상 긍정은 아니다. 처리량 상승은 중립, lag/backlog/탐지율 급등은 주의가 될 수 있으므로 KPI별 semantic direction을 정의한다.
- `+12.4%`, `+18건` 같은 delta chip은 데이터가 실제로 변경된 경우 일정 시간 표시하되, 급격히 사라져 읽기 어렵지 않게 최소 3~5초 유지한다.
- CRITICAL 상태 변경은 restrained attention animation을 최대 2회까지만 수행하고 무한 반복하지 않는다.
- 스크린리더에는 중간 count 값이 아니라 최종 값만 한 번 알려준다.

## 7. 실무 수준의 파이프라인 처리량 시계열

`TimeSeriesChart`를 단순 데모 차트가 아닌 Grafana/Datadog 계열의 운영 문법을 참고한 전문적인 관제 차트로 고도화하라. 특정 제품을 복제하지는 마라.

### 7.1 시각 구조

- 수집 처리량은 translucent area 또는 restrained vertical bars 중 데이터 밀도에 더 적합한 방식을 선택한다.
- 탐지 이벤트는 명확한 solid line + point-on-hover로 표시한다.
- 탐지율은 우측 축의 dashed line으로 유지하되 색·dash·범례로 확실히 구분한다.
- 왼쪽 축은 `건수`, 오른쪽 축은 `%` 라벨을 명시한다.
- 0 baseline을 유지하고, 자동 축 범위 때문에 작은 변동이 과장되지 않게 한다.
- 최신 bucket이 아직 완료되지 않은 partial bucket이면 hatch/dashed opacity와 `집계 중` 레이블로 구분한다. 이를 판별할 API 정보가 없다면 백엔드 계약 확장 또는 bucket 시간 기반의 결정론적 판별을 사용하고 추측을 문서화한다.
- 빈 bucket과 missing data를 모두 0으로 그리지 않는다. `null`과 `0`을 분리할 수 있도록 타입/API 계약을 검토한다.
- 데이터 공백은 선을 연결하지 않고 gap으로 보여준다.

### 7.2 갱신 애니메이션

- 같은 range/bucket의 polling 갱신에서는 기존 point가 새 위치로 250~400ms morph/transition한다.
- 오른쪽에 새 bucket이 추가될 때 plot이 짧게 slide/reveal되며 시간의 진행 방향을 보여준다.
- 전체 SVG를 fade-out/fade-in 하지 않는다. 사용자가 시계열 연속성을 잃는다.
- range 변경(1h/6h/24h)은 실시간 갱신과 구분해 160~220ms crossfade 또는 clip reveal을 적용한다.
- line은 최초 진입 시 한 번만 짧게 draw-in 가능하지만 polling마다 path drawing을 반복하지 않는다.
- 막대는 baseline에서 grow하는 animation을 최초 로드 또는 실제 신규 bucket에만 적용한다.
- path interpolation을 직접 구현할 경우 동일 bucket key 기준으로 좌표를 매핑하고, point 수가 다른 경우의 정책을 명확히 한다. 불안정한 string path interpolation은 금지한다.
- 외부 animation/chart 라이브러리를 추가하기 전에 기존 SVG로 가능한지 판단한다. 의존성을 추가하면 선택 이유, bundle 영향, reduced-motion 지원을 보고한다.

### 7.3 Interaction

- pointer가 차트 안에 들어오면 nearest bucket을 찾는 vertical crosshair와 모든 series 값을 담은 unified tooltip을 제공한다.
- hit rect를 point마다 수십 개 focus target으로 노출하는 현재 방식은 키보드 사용 시 과도할 수 있다. 차트 전체를 하나의 focusable composite widget로 만들고 좌우 방향키로 bucket을 이동하는 방식을 검토·구현한다.
- tooltip은 viewport 가장자리에서 잘리지 않도록 좌우 위치를 자동 조정한다.
- tooltip 숫자 정렬은 tabular-nums, series 순서는 범례와 동일하게 한다.
- 선택한 bucket은 click/touch 후 유지되며 `Esc` 또는 외부 click으로 해제한다.
- 모바일에서는 hover 없이 tap과 좌우 drag/scrub으로 값을 확인할 수 있게 한다. 스크롤을 방해하지 않도록 수평 gesture 영역을 신중히 처리한다.
- 범례를 클릭하면 series를 숨기거나 다시 표시할 수 있게 하며, 최소 한 series는 유지한다. 상태는 `aria-pressed`로 노출한다.

### 7.4 이상 징후 표시

- `throughputInsight`와 동일한 결정론적 규칙을 사용해 급증 bucket에 작은 anomaly marker를 표시한다.
- 수집·탐지 동반 급증, 탐지만 급증, 수집만 급증을 서로 다른 아이콘/패턴과 텍스트로 설명한다.
- marker는 pulse하지 않고 최초 발견 시 1회 강조한다.
- threshold 또는 기준선이 존재하면 선과 label을 제공하고, 기준이 없는데 임의 threshold를 만들지 않는다.
- insight 문구와 차트 marker가 동일 bucket을 가리키도록 단일 source of truth를 사용한다.

### 7.5 Zoom과 범위

- 기본 1h/6h/24h toggle은 유지한다.
- brush zoom은 데이터 양과 실제 사용 가치가 있을 때만 도입한다. 현재 최대 24개 안팎 bucket이면 불필요한 zoom UI를 추가하지 않는다.
- 범위 변경 중 기존 차트는 유지하고 작은 updating overlay만 표시한다.

## 8. 신규 이벤트와 표의 실시간 UX

조사 큐, 실시간 탐지 피드, 트랜잭션 피드에 다음을 적용한다.

- 이전 응답에 없던 `event.id` 또는 `txHash`만 신규 항목으로 판별한다.
- 신규 행은 220~320ms의 짧은 background tint + opacity/translateY 진입을 1회 실행한다.
- 정렬 때문에 기존 행이 이동할 때 과도한 FLIP animation으로 표 전체가 흔들리지 않게 한다. 최대 8개 조사 큐에서만 필요성을 판단한다.
- 사용자가 표를 읽는 중 신규 데이터가 도착해 행 위치가 바뀌는 문제를 줄인다. 포커스·hover·선택 중이면 즉시 재정렬하지 않고 `새 이벤트 3건` 버튼으로 배치 반영하는 전략을 검토한다.
- 새 이벤트가 CRITICAL이면 페이지 제목 또는 헤더의 배지로 제한적으로 알리되, 소리·브라우저 알림은 사용자 명시적 opt-in 없이는 사용하지 않는다.
- “신규” 강조는 5~8초 후 평상 상태로 돌아가며 DOM 의미와 데이터 값은 유지한다.
- 행 삭제/해결 상태 반영은 opacity exit 후 제거할 수 있지만 운영 결과가 갑자기 사라지지 않도록 상태 변경 이유와 이동 위치를 알린다.

## 9. Pipeline Health Strip 상태 전환

- 구성요소 상태가 UP → DOWN, DOWN → UP, DISABLED 등으로 실제 변경될 때만 전환 애니메이션을 실행한다.
- DOWN 전환은 1회 border/indicator 강조와 `상태 변경 시각`을 제공한다.
- 복구는 녹색 pulse 대신 차분한 success tint와 `복구됨` 텍스트를 잠시 표시한다.
- downstream `영향 가능` 상태가 구조적으로 표현되며 애니메이션만으로 의미를 전달하지 않는다.
- 파이프라인 연결선에 계속 흐르는 particle은 금지한다. 실제 처리량을 정확히 매핑할 수 있을 때만 선 굵기 또는 미세한 단방향 progress를 고려한다.

## 10. Loading·Error·Empty·Stale 전환

- 최초 로드에만 skeleton을 사용한다. skeleton은 실제 레이아웃과 동일한 크기로 CLS를 방지한다.
- background refresh에서는 skeleton을 다시 띄우지 않는다.
- error banner가 나타날 때 레이아웃을 크게 밀어내지 않도록 영역을 예약하거나 compact inline status를 사용한다.
- empty state가 데이터 수신 후 table/chart로 바뀔 때 160~220ms opacity transition을 적용한다.
- 에러가 복구되면 성공 toast를 남발하지 말고 상태 cluster와 마지막 갱신 시각으로 조용히 피드백한다.

## 11. 추가로 반드시 보완할 Senior UX 항목

10년차 디자이너/프론트엔드 관점에서 아래도 구현 또는 타당성 검토 후 반영하라.

### 11.1 정보 우선순위

- 첫 viewport에서 LIVE 상태, 4 KPI, 시계열 핵심 plot이 1440×900 기준으로 읽히는지 검증한다.
- 차트 높이는 데이터 분석에 충분하되 하단 핵심 정보를 과도하게 밀어내지 않는다.
- 현재 `파이프라인 상태`와 `관리자 파이프라인`의 중복 정보를 정리하고 Overview에는 의사결정에 필요한 요약만 둔다.

### 11.2 색상과 시각 인코딩

- 동일 색을 다른 의미에 재사용하지 않는다. 예: blue=collected, amber/red=detected risk, violet 또는 neutral dashed=rate처럼 일관된 series token을 둔다.
- dark/light 모두 WCAG AA 대비를 확인한다.
- 색각 이상 사용자를 위해 선 dash, marker shape, label을 함께 사용한다.
- anomaly와 장애 색상은 일반 hover/selection 색상과 구분한다.

### 11.3 숫자·시간·단위

- y축, tooltip, KPI, 표의 compact format 정책을 하나의 formatter로 통일한다.
- `건/분`, `건`, `%`, `block`, `confirmation` 단위를 생략하지 않는다.
- 브라우저 로컬 시간대 또는 Asia/Seoul 사용 정책을 명시하고 차트/tooltip/표에서 통일한다.
- 자정·날짜 경계에서는 x축에 날짜도 표시한다.

### 11.4 URL과 사용자 상태

- 선택 range를 URL query 또는 session state에 보존해 새로고침/공유 시 유지한다.
- legend series visibility, 선택 bucket 같은 임시 상태는 과도하게 URL에 넣지 않는다.
- 브라우저 뒤로가기로 range가 자연스럽게 복원되는지 확인한다.

### 11.5 관측 가능성

- 프론트 데이터 fetch duration, 실패 위젯, stale 전환을 개발 환경에서 추적 가능한 구조로 만든다.
- console.log를 남발하지 말고 기존 observability 전략이 없으면 최소한 구조화된 개발용 debug helper 수준으로 제한한다.
- 애니메이션 자체가 main thread를 막지 않는지 Performance panel 또는 동등한 측정으로 확인한다.

### 11.6 반응형

- 1440×900, 1280×800, 960×900, 768×1024, 390×844에서 확인한다.
- 모바일 차트는 축 레이블을 줄이되 tooltip 데이터는 유지한다.
- 작은 화면에서 범례가 plot을 압박하지 않도록 아래로 wrap하거나 compact toggle로 바꾼다.
- table은 중요한 열을 우선 노출하고 단순 horizontal scroll에만 의존하지 않는 대체 row layout을 검토한다.

## 12. 상태 모델과 API 정직성

- polling 기반이면 UI에서 WebSocket 기반처럼 오해시키지 않는다. `30초 자동 갱신`을 명확히 유지한다.
- 정말 더 짧은 실시간성이 필요하다고 판단해 SSE/WebSocket을 제안하더라도, 백엔드와 운영 복잡도를 분석하고 별도 단계로 구현한다. 단순 애니메이션을 위해 프로토콜을 바꾸지 않는다.
- 데이터가 측정되지 않은 상태는 `UNKNOWN/—`로 표시하며 0으로 만들지 않는다.
- partial bucket, missing bucket, delayed event의 의미를 API 계약과 UI에서 일치시킨다.
- 프론트 타임스탬프와 서버 `generatedAt`을 구분한다. 데이터 freshness는 가능하면 서버 생성 시각을 기준으로 한다.

## 13. 구현 구조 권장안

실제 저장소에 맞게 조정하되 한 파일에 몰아넣지 마라.

```text
frontend/src/
  components/
    LiveStatusCluster.tsx
    AnimatedMetricValue.tsx
    TimeSeriesChart.tsx
    ChartTooltip.tsx
    ChartLegend.tsx
  hooks/
    useOverviewData.ts
    usePrevious.ts
    usePrefersReducedMotion.ts
    useCountdown.ts
  lib/
    overviewDiff.ts
    chartGeometry.ts
    motion.ts
```

- 차트 geometry, diff, formatter, anomaly 계산은 순수 함수로 분리한다.
- React state는 차트 hover/selection, query, 서버 데이터, animation state를 구분한다.
- `requestAnimationFrame`, timer, event listener는 cleanup한다.
- StrictMode의 effect 이중 실행에서도 중복 timer와 중복 애니메이션이 발생하지 않게 한다.
- SVG `id` 충돌을 피하고 여러 차트 인스턴스에도 안전하게 한다.

## 14. 성능 예산

- animation 중 지속적으로 React tree 전체를 rerender하지 않는다.
- countdown, count-up은 작은 component boundary로 격리한다.
- `requestAnimationFrame` callback에서 layout read/write를 섞지 않는다.
- SVG point가 많아질 가능성을 고려해 24h보다 큰 범위를 추가할 경우 downsampling 전략을 문서화한다.
- 불필요한 `will-change`를 상시 적용하지 않는다.
- 새 라이브러리 추가 전후 production bundle gzip 크기를 비교한다.
- 차트 interaction 중 50ms 이상의 long task가 없도록 목표를 둔다.

## 15. 접근성

- 모든 애니메이션은 reduced motion에서 의미 손실 없이 동작한다.
- `aria-live`는 LIVE 영역 전체가 아니라 최종 상태 변경과 중요한 신규 CRITICAL 이벤트에만 제한한다.
- polling마다 화면 전체가 다시 낭독되지 않게 한다.
- 차트에 텍스트 summary 또는 접근 가능한 data table 대체를 제공한다.
- 키보드로 차트 진입, 좌우 bucket 이동, legend toggle, 선택 해제가 가능해야 한다.
- focus ring은 차트 배경과 light/dark 모두에서 보인다.
- tooltip은 hover 전용 정보가 아니며 focus/tap으로도 접근 가능해야 한다.

## 16. 테스트

반드시 다음을 추가·실행하라.

### Unit

- 이전/현재 overview diff 계산
- 최초 로드, unchanged, up/down, null 전환, stale 응답
- range 변경과 polling 갱신 구분
- 신규 이벤트 ID/txHash 판별
- chart geometry와 nearest bucket
- missing/zero/null bucket 처리
- anomaly marker와 insight의 동일 source 검증
- reduced-motion 분기

### Component

- KPI 최종 값과 delta 렌더
- 갱신 성공 시 1회 highlight, 실패 시 미실행
- LIVE/UPDATING/STALE/PAUSED/OFFLINE 상태
- 차트 keyboard navigation 및 legend toggle
- tooltip edge positioning
- background refresh에서 기존 chart 유지

### Verification

- `npm test`
- `npm run build`
- 필요 시 backend 계약을 변경했다면 `./gradlew test`
- 브라우저 console error 0건
- 위 지정 viewport의 light/dark 확인
- reduced motion emulation 확인
- 네트워크 slow/offline, 부분 API 실패, 탭 hide/show, 연속 수동 새로고침 확인

테스트를 위해 fake timer와 고정 timestamp를 사용해 flaky animation test를 만들지 마라. transition의 모든 중간 frame을 snapshot 테스트하지 말고 상태·최종 결과·class/attribute 계약을 테스트한다.

## 17. 완료 기준

다음 조건을 모두 만족해야 완료다.

1. 데이터 변경 전후가 시각적으로 연결되며 무엇이 변했는지 알 수 있다.
2. 애니메이션이 실제 데이터 변화 때만 실행된다.
3. 시계열이 수집·탐지·탐지율, missing/partial bucket, anomaly를 정직하게 표현한다.
4. polling 갱신이 차트 전체 깜빡임이나 레이아웃 점프를 만들지 않는다.
5. 키보드·touch·reduced motion에서 기능이 유지된다.
6. stale/paused/offline이 명확히 구분된다.
7. 실무 운영 콘솔다운 밀도와 절제된 모션을 유지한다.
8. 테스트와 production build가 통과한다.

## 18. 최종 보고 형식

작업 완료 후 아래 순서로 보고하라.

1. 발견한 기존 UX 문제
2. 구축한 motion system과 사용 원칙
3. 데이터 diff 및 실시간 상태 모델
4. 시계열 차트 개선 내용
5. 신규 이벤트/파이프라인 상태 전환 UX
6. 접근성·reduced motion 대응
7. 성능 측정과 bundle 변화
8. 반응형 브라우저 검증 결과
9. 실행한 테스트와 결과
10. 변경 파일 목록
11. 남은 한계와 후속 우선순위

기존 사용자 변경사항을 덮어쓰거나 관련 없는 코드를 정리하지 마라. 구현 중 API 의미가 불분명하면 0이나 정상으로 추측하지 말고 UNKNOWN 상태를 유지하고, 필요한 최소 계약만 명시적으로 확장하라.

---

## 이 프롬프트가 지향하는 결과

> 움직이는 대시보드가 아니라, 실제 데이터의 도착·변화·지연·장애를 운영자가 빠르게 이해하고 신뢰할 수 있는 거래소급 실시간 관제 인터페이스를 만든다.
