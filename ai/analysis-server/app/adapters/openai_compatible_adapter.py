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

    async def generate(self, prompt: str, system: str | None = None) -> AdapterResult:
        url = f"{self._base_url}/v1/chat/completions"
        messages = []
        if system:
            messages.append({"role": "system", "content": system})
        messages.append({"role": "user", "content": prompt})
        payload = {"model": self._model, "messages": messages}

        try:
            async with httpx.AsyncClient(timeout=self._timeout) as client:
                response = await client.post(url, json=payload)
                response.raise_for_status()
                body = response.json()
        except httpx.HTTPError as error:
            raise ModelAdapterError(f"{self.name} request failed: {error}") from error
        except ValueError as error:
            raise ModelAdapterError(f"{self.name} returned invalid JSON: {error}") from error

        try:
            content = body["choices"][0]["message"]["content"]
        except (KeyError, IndexError, TypeError) as error:
            raise ModelAdapterError(f"Unexpected {self.name} response shape: {error}") from error

        if not isinstance(content, str):
            raise ModelAdapterError(f"Unexpected {self.name} content type: {type(content).__name__}")

        report = content.strip()
        if not report:
            raise ModelAdapterError(f"{self.name} returned an empty report")

        return AdapterResult(report=report, raw_response=json.dumps(body, ensure_ascii=False), model=self._model)
