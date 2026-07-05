"""Adapter for OpenAI-compatible local servers (LM Studio, Hermes)."""

import json

import httpx

from app.adapters.base import AdapterResult, ModelAdapter, ModelAdapterError


class OpenAiCompatibleAdapter(ModelAdapter):
    def __init__(self, name: str, base_url: str, model: str, timeout_seconds: float):
        self.name = name
        self._base_url = base_url.rstrip("/")
        self._model = model
        self._timeout = timeout_seconds

    async def generate(self, prompt: str) -> AdapterResult:
        url = f"{self._base_url}/v1/chat/completions"
        payload = {
            "model": self._model,
            "messages": [{"role": "user", "content": prompt}],
        }

        try:
            async with httpx.AsyncClient(timeout=self._timeout) as client:
                response = await client.post(url, json=payload)
                response.raise_for_status()
                body = response.json()
        except httpx.HTTPError as error:
            raise ModelAdapterError(f"{self.name} request failed: {error}") from error

        try:
            report = (body["choices"][0]["message"]["content"] or "").strip()
        except (KeyError, IndexError, TypeError) as error:
            raise ModelAdapterError(f"Unexpected {self.name} response shape: {error}") from error

        if not report:
            raise ModelAdapterError(f"{self.name} returned an empty report")

        return AdapterResult(report=report, raw_response=json.dumps(body, ensure_ascii=False), model=self._model)
