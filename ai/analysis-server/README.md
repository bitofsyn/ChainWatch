# ChainWatch AI Analysis Server

탐지 이벤트(DetectionEvent)를 자연어 분석 리포트로 변환하는 FastAPI 서버입니다.
백엔드의 `FastApiAiAnalysisClient`가 호출하는 대상이며, 요청/응답 계약(camelCase)에 맞춰 구현되어 있습니다.

## 아키텍처

```
Backend (Spring) ──POST /api/v1/analyze──▶ AnalysisService
                                              │  Provider 선택 → Retry → Fallback
                                              ▼
                     ┌──────────┬──────────┬────────────┬──────────┐
                     │  Claude  │  Gemini  │  LM Studio │  Hermes  │ ... Mock(항상)
                     └──────────┴──────────┴────────────┴──────────┘
```

- **Model Adapter 패턴**: `app/adapters/base.py`의 `ModelAdapter` 인터페이스를 각 프로바이더가 구현
- **Retry**: 프로바이더별 `CHAINWATCH_AI_RETRY_MAX_ATTEMPTS`회 시도. 시도할 때마다 대기 시간이 늘어남 (`CHAINWATCH_AI_RETRY_BACKOFF_SECONDS` × 시도 횟수)
- **Fallback**: 실패 시 `CHAINWATCH_AI_FALLBACK_CHAIN` 순서로 다음 프로바이더 시도
- **Gemini 예비 모델**: 기본 모델이 과부하(503)·한도 초과(429)·미지원(404)·응답 지연이면 `CHAINWATCH_AI_GEMINI_FALLBACK_MODELS` 순서로 같은 요청을 다시 보냄. 리포트에는 실제로 응답한 모델이 기록됨
- **Mock Adapter**: 항상 등록되지만 기본 폴백 체인에는 없음. 실제 LLM이 모두 실패했을 때 가짜 리포트로 덮지 않도록, 요청에 `provider: "mock"`을 주거나 체인에 넣었을 때만 사용 (로컬 개발/테스트용)

## API

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/v1/analyze` | 탐지 이벤트 분석 리포트 생성 |
| GET | `/api/v1/providers` | 사용 가능한 프로바이더 목록 |
| GET | `/health` | 헬스체크 |

`CHAINWATCH_AI_API_TOKEN`을 설정하면 `/api/v1/analyze`와 `/api/v1/providers`는 같은 값을 `X-ChainWatch-AI-Token` 헤더로 보낸 요청만 처리하고, 그 외에는 `401`을 돌려줍니다. `/health`는 토큰 없이 열려 있습니다.

### POST /api/v1/analyze

요청 (백엔드 `AiAnalysisRequest`와 동일):

```json
{
  "detectionEventId": 1,
  "provider": "claude",
  "model": null,
  "eventType": "LARGE_TRANSFER",
  "riskLevel": "HIGH",
  "riskScore": 90,
  "walletAddress": "0x...",
  "txHash": "0x...",
  "summary": "고액 이체 탐지"
}
```

응답 (prompts v3, 구조화 필드는 additive):

```json
{
  "report": "...분석 리포트...",
  "rawResponse": "...모델 원본 응답...",
  "provider": "claude",
  "model": "claude-opus-4-8",
  "promptVersion": "v3",
  "structured": true,
  "riskSummary": "핵심 위험 요약",
  "evidence": [{"source": "riskScore", "fact": "위험 점수 90"}],
  "possibleScenarios": ["..."],
  "recommendedActions": ["..."],
  "confidence": "medium",
  "falsePositiveFactors": ["..."],
  "escalationLevel": "monitor"
}
```

- `confidence`: `low | medium | high`, `escalationLevel`: `none | monitor | escalate | urgent`
- LLM 출력이 JSON 스키마로 파싱되지 않으면 에러 대신 `structured=false` + 텍스트 `report`만으로 degrade
- 이벤트 유래 값은 신뢰할 수 없는 데이터로 취급되어 sentinel 블록 안에만 전달됨 (프롬프트 인젝션 방어)

모든 프로바이더 실패 시 `502` + `{"code": "ANALYSIS_FAILED", "message": "..."}`.

## 환경 변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `CHAINWATCH_AI_DEFAULT_PROVIDER` | `claude` | 요청에 provider가 없을 때 사용 |
| `CHAINWATCH_AI_FALLBACK_CHAIN` | `claude,gemini,lmstudio,hermes` | 폴백 순서 (mock 제외) |
| `CHAINWATCH_AI_RETRY_MAX_ATTEMPTS` | `2` | 프로바이더별 시도 횟수 |
| `CHAINWATCH_AI_RETRY_BACKOFF_SECONDS` | `0.5` | 재시도 대기 시간 기본값 |
| `CHAINWATCH_AI_HTTP_TIMEOUT_SECONDS` | `60` | Claude·로컬 LLM 요청 제한 시간 |
| `CHAINWATCH_AI_API_TOKEN` | (없음) | 백엔드와 공유하는 토큰. 비어 있으면 검사하지 않음 |
| `CHAINWATCH_AI_ANTHROPIC_API_KEY` | (없음) | 미설정 시 `ANTHROPIC_API_KEY` 사용 |
| `CHAINWATCH_AI_CLAUDE_MODEL` | `claude-opus-4-8` | Claude 모델 ID |
| `CHAINWATCH_AI_GEMINI_API_KEY` | (없음) | 설정 시 Gemini 어댑터 활성화 |
| `CHAINWATCH_AI_GEMINI_MODEL` | `gemini-flash-latest` | Gemini 기본 모델 ID |
| `CHAINWATCH_AI_GEMINI_FALLBACK_MODELS` | `gemini-flash-lite-latest,gemini-2.5-flash` | 기본 모델이 실패할 때 시도할 예비 모델 |
| `CHAINWATCH_AI_GEMINI_TIMEOUT_SECONDS` | `30` | Gemini 모델 하나당 요청 제한 시간 |
| `CHAINWATCH_AI_LMSTUDIO_BASE_URL` | (없음) | 예: `http://localhost:1234` |
| `CHAINWATCH_AI_HERMES_BASE_URL` | (없음) | OpenAI 호환 엔드포인트 |

API 키/엔드포인트가 설정된 프로바이더만 어댑터로 등록되며, Mock은 항상 등록됩니다.

## 실행

```bash
cd ai/analysis-server
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --port 8000
```

백엔드 연동 (application-local.yml 등):

```yaml
chainwatch:
  ai:
    enabled: true
    base-url: http://localhost:8000
    analyze-path: /api/v1/analyze
    api-token: ""   # AI 서버의 CHAINWATCH_AI_API_TOKEN과 같은 값 (환경변수 CHAINWATCH_AI_API_TOKEN)
```

## 테스트

```bash
pip install -r requirements-dev.txt
pytest
```
