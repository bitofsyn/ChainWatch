# WAVE1 AI 분석 컨트랙트 (prompts v3, structured output)

AI 분석 서버(`ai/analysis-server`)와 백엔드(`backend/.../analysis`) 간, 그리고
백엔드와 프론트엔드 간의 AI 분석 응답 계약 문서. 프론트엔드는 이 문서를 기준으로
구조화 리포트 UI를 렌더링한다.

## 1. 요청 (변경 없음)

`POST /api/v1/analyze` (AI 서버) — 기존 필드 그대로, 추가/변경 없음.

```json
{
  "detectionEventId": 1,
  "provider": null,
  "model": null,
  "eventType": "LARGE_TRANSFER",
  "riskLevel": "HIGH",
  "riskScore": 90,
  "walletAddress": "0xabc",
  "txHash": "0xdef",
  "summary": "고액 이체 탐지"
}
```

`summary`는 유일한 필수 필드. 나머지는 nullable.

## 2. AI 서버 응답 (신규 필드 — 전부 additive)

기존 필드(`report`, `rawResponse`, `provider`, `model`, `promptVersion`)는 그대로
유지된다. `promptVersion`은 `"v3"`로 올라갔다. 신규 필드:

| 필드 | 타입 | 설명 |
|---|---|---|
| `structured` | boolean | LLM 출력이 구조화 스키마로 파싱됐는지 여부. `false`면 아래 필드는 전부 비어 있고 `report` 텍스트만 신뢰 |
| `riskSummary` | string \| null | 핵심 위험 요약 1~2문장 |
| `evidence` | `[{source, fact}]` | 제공된 입력 데이터에 근거한 사실 목록. `source`는 데이터 필드명(예: `riskScore`), `fact`는 확인된 사실 |
| `possibleScenarios` | string[] | 데이터 범위 내 가능한 시나리오 |
| `recommendedActions` | string[] | 우선순위 순 권장 조치 (최대 4개) |
| `confidence` | string \| null | **`"low" \| "medium" \| "high"`** (문자열 3단계로 확정, 0-1 숫자 아님) |
| `falsePositiveFactors` | string[] | 오탐 가능 요인 |
| `escalationLevel` | string \| null | **`"none" \| "monitor" \| "escalate" \| "urgent"`** |

### 성공(구조화) 응답 예시

```json
{
  "report": "## 개요\n대량 이체 이벤트에 대한 상세 리포트...",
  "rawResponse": "{...provider raw payload...}",
  "provider": "claude",
  "model": "claude-opus-4-8",
  "promptVersion": "v3",
  "structured": true,
  "riskSummary": "고위험 대량 이체 정황이 탐지되었습니다.",
  "evidence": [
    {"source": "riskScore", "fact": "위험 점수 90 (HIGH 등급)"},
    {"source": "summary", "fact": "고액 이체 탐지"}
  ],
  "possibleScenarios": ["자금 세탁 목적의 분산 이동", "대형 보유자 리밸런싱(정상)"],
  "recommendedActions": ["지갑 주소 워치리스트 등재 - 반복 탐지 시 즉시 알림 확보", "상대 주소 거래소 태그 확인"],
  "confidence": "medium",
  "falsePositiveFactors": ["거래소 내부 이동 가능성", "상대 주소 정보 부재"],
  "escalationLevel": "escalate"
}
```

### 성능 저하(degraded) 응답 예시

LLM 출력이 JSON 스키마로 파싱되지 않으면 에러 대신 텍스트 전용으로 degrade한다:

```json
{
  "report": "자유 서술 리포트 원문...",
  "rawResponse": "{...}",
  "provider": "claude",
  "model": "claude-opus-4-8",
  "promptVersion": "v3",
  "structured": false,
  "riskSummary": null,
  "evidence": [],
  "possibleScenarios": [],
  "recommendedActions": [],
  "confidence": null,
  "falsePositiveFactors": [],
  "escalationLevel": null
}
```

모든 프로바이더 실패 시 기존과 동일하게 502 + `{"code": "ANALYSIS_FAILED", "message": "..."}`.

## 3. 백엔드 API 응답 (프론트엔드가 실제로 보는 형태)

`POST /api/events/{eventId}/analysis`, `POST /api/events/{eventId}/analysis/async`,
이벤트 상세 조회의 `analysis` 필드(`AiAnalysisReportResponse`)에 추가된 필드:

| 필드 | 타입 | 설명 |
|---|---|---|
| `structured` | boolean | 구조화 분석 존재 여부 |
| `structuredAnalysis` | object \| null | 아래 형태. `structured=false`면 null |

```json
{
  "id": 10,
  "status": "COMPLETED",
  "provider": "claude",
  "model": "claude-opus-4-8",
  "promptSummary": "고액 이체 탐지",
  "report": "## 개요\n...",
  "analyzedAt": "2026-07-08T00:00:00Z",
  "structured": true,
  "structuredAnalysis": {
    "riskSummary": "고위험 대량 이체 정황이 탐지되었습니다.",
    "evidence": [{"source": "riskScore", "fact": "위험 점수 90 (HIGH 등급)"}],
    "possibleScenarios": ["자금 세탁 목적의 분산 이동"],
    "recommendedActions": ["지갑 주소 워치리스트 등재 - 반복 탐지 시 즉시 알림 확보"],
    "confidence": "medium",
    "falsePositiveFactors": ["거래소 내부 이동 가능성"],
    "escalationLevel": "escalate"
  }
}
```

- 기존(구버전) 리포트, PENDING/FAILED 리포트, degraded 리포트는 `structured=false`,
  `structuredAnalysis=null`이며 기존처럼 `report` 텍스트를 표시한다.
- `rawResponse`는 감사/디버그용으로 DB에만 저장되고 API로 노출되지 않는다(기존과 동일).

## 4. UI 렌더링 가이드 (프론트엔드 에이전트용)

- AI 리포트는 **보조 근거(assistant evidence)**로 표시하고 확정 판단처럼 보이게 하지 말 것.
- `confidence`(low/medium/high)와 `escalationLevel`(none/monitor/escalate/urgent)을 배지 등으로 노출.
- `structured=false`인 경우: 기존 마크다운 `report` 렌더링으로 폴백 + "구조화 분석 불가" 안내.
- `evidence`가 비어 있으면 "근거 데이터 없음"을 명시 (모델이 근거 없이 판단했을 수 있음).
- 분석 실패(`status=FAILED`)는 재시도 버튼(async 엔드포인트 재호출) 제공.

## 5. 안전 관련 계약

- 프롬프트 v3는 이벤트 유래 값(요약, 지갑 주소 등)을 전부 신뢰할 수 없는 데이터로 취급해
  `<<<UNTRUSTED_EVENT_DATA>>> ... <<<END_UNTRUSTED_EVENT_DATA>>>` 블록 안에 JSON으로만 전달하고,
  값 안에 sentinel 문자열이 있으면 `[FILTERED]`로 치환한다(블록 탈출 방지).
- 지시문은 시스템 프롬프트 채널로만 전달된다 (Claude `system`, Gemini `systemInstruction`,
  OpenAI 호환 `role=system`).
- AI 출력은 표시용이며, 백엔드/프론트는 AI 출력으로 관리자 액션을 자동 실행하지 않는다.
- `confidence`/`escalationLevel`은 서버에서 허용값으로 정규화되며, 정규화 불가하면
  응답 전체가 `structured=false`로 degrade된다 (부분적으로 잘못된 값이 내려가지 않음).
