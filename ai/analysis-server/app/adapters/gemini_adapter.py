"""Gemini adapter using the Google Generative Language REST API."""

import json

import httpx

from app.adapters.base import AdapterResult, ModelAdapter, ModelAdapterError
from app.config import Settings
from app.constants import PROVIDER_GEMINI


class GeminiAdapter(ModelAdapter):
    name = PROVIDER_GEMINI

    def __init__(self, settings: Settings):
        self._base_url = settings.gemini_base_url.rstrip("/")
        self._api_key = settings.gemini_api_key
        self._model = settings.gemini_model
        self._timeout = settings.http_timeout_seconds

    async def generate(self, prompt: str) -> AdapterResult:
        url = f"{self._base_url}/v1beta/models/{self._model}:generateContent"
        payload = {"contents": [{"parts": [{"text": prompt}]}]}
        headers = {"x-goog-api-key": self._api_key}

        try:
            async with httpx.AsyncClient(timeout=self._timeout) as client:
                response = await client.post(url, json=payload, headers=headers)
                response.raise_for_status()
                body = response.json()
        except httpx.HTTPError as error:
            raise ModelAdapterError(f"Gemini request failed: {error}") from error

        try:
            parts = body["candidates"][0]["content"]["parts"]
            report = "".join(part.get("text", "") for part in parts).strip()
        except (KeyError, IndexError, TypeError) as error:
            raise ModelAdapterError(f"Unexpected Gemini response shape: {error}") from error

        if not report:
            raise ModelAdapterError("Gemini returned an empty report")

        return AdapterResult(report=report, raw_response=json.dumps(body, ensure_ascii=False), model=self._model)
