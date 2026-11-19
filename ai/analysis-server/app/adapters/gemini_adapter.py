"""Gemini adapter using the Google Generative Language REST API."""

import json
import logging

import httpx

from app.adapters.base import AdapterResult, ModelAdapter, ModelAdapterError
from app.config import Settings
from app.constants import PROVIDER_GEMINI

logger = logging.getLogger(__name__)

# 같은 요청을 다른 모델로 보내면 해결될 수 있는 응답: 과부하·일시 장애·한도 초과·모델 미지원.
RETRY_NEXT_MODEL_STATUSES = {404, 429, 500, 502, 503, 504}


class GeminiAdapter(ModelAdapter):
    name = PROVIDER_GEMINI

    def __init__(self, settings: Settings):
        self._base_url = settings.gemini_base_url.rstrip("/")
        self._api_key = settings.gemini_api_key
        self._models = settings.gemini_models()
        self._timeout = settings.http_timeout_seconds

    async def generate(self, prompt: str, system: str | None = None) -> AdapterResult:
        payload: dict = {
            "contents": [{"parts": [{"text": prompt}]}],
            # 프롬프트 지시에만 의존하지 않고 API 레벨에서 JSON 출력을 강제한다.
            # (report_parser의 lenient 파싱은 안전망으로 유지)
            "generationConfig": {"responseMimeType": "application/json", "temperature": 0.2},
        }
        if system:
            payload["systemInstruction"] = {"parts": [{"text": system}]}

        last_error: ModelAdapterError | None = None
        for model in self._models:
            try:
                body = await self._post(model, payload)
            except _RetryableModelError as error:
                logger.warning("Gemini model %s unavailable (%s); trying next model", model, error.status)
                last_error = ModelAdapterError(f"Gemini request failed: {error.status} on {model}")
                continue
            return self._to_result(body, model)
        raise last_error or ModelAdapterError("No Gemini model configured")

    async def _post(self, model: str, payload: dict) -> dict:
        url = f"{self._base_url}/v1beta/models/{model}:generateContent"
        headers = {"x-goog-api-key": self._api_key}
        try:
            async with httpx.AsyncClient(timeout=self._timeout) as client:
                response = await client.post(url, json=payload, headers=headers)
                if response.status_code in RETRY_NEXT_MODEL_STATUSES:
                    raise _RetryableModelError(response.status_code)
                response.raise_for_status()
                return response.json()
        except _RetryableModelError:
            raise
        except httpx.HTTPError as error:
            raise ModelAdapterError(f"Gemini request failed: {error}") from error
        except ValueError as error:
            raise ModelAdapterError(f"Gemini returned invalid JSON: {error}") from error

    @staticmethod
    def _to_result(body: dict, model: str) -> AdapterResult:
        try:
            parts = body["candidates"][0]["content"]["parts"]
            report = "".join(part.get("text", "") for part in parts).strip()
        except (KeyError, IndexError, TypeError, AttributeError) as error:
            raise ModelAdapterError(f"Unexpected Gemini response shape: {error}") from error

        if not report:
            raise ModelAdapterError("Gemini returned an empty report")

        return AdapterResult(report=report, raw_response=json.dumps(body, ensure_ascii=False), model=model)


class _RetryableModelError(Exception):
    def __init__(self, status: int):
        super().__init__(f"HTTP {status}")
        self.status = status
