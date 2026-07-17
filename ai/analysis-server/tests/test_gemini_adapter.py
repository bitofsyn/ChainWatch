"""GeminiAdapter HTTP 경로 테스트 — httpx.MockTransport로 실제 네트워크 없이 검증한다."""

import json

import httpx
import pytest

from app.adapters.base import ModelAdapterError
from app.adapters.gemini_adapter import GeminiAdapter
from app.config import Settings


def _settings() -> Settings:
    return Settings(
        gemini_api_key="test-gemini-key",
        gemini_model="gemini-2.5-flash",
        gemini_base_url="https://generativelanguage.googleapis.com",
        http_timeout_seconds=5.0,
    )


def _patch_transport(monkeypatch, handler):
    """어댑터 내부의 httpx.AsyncClient가 MockTransport를 쓰도록 바꾼다."""
    transport = httpx.MockTransport(handler)
    original_client = httpx.AsyncClient

    class TransportInjectingClient(original_client):
        def __init__(self, **kwargs):
            kwargs["transport"] = transport
            super().__init__(**kwargs)

    monkeypatch.setattr(httpx, "AsyncClient", TransportInjectingClient)


def _gemini_ok_body(text: str) -> dict:
    return {"candidates": [{"content": {"parts": [{"text": text}]}}]}


async def test_generate_sends_expected_request_and_parses_text(monkeypatch):
    captured: dict = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["api_key"] = request.headers.get("x-goog-api-key")
        captured["payload"] = json.loads(request.content)
        return httpx.Response(200, json=_gemini_ok_body('{"riskSummary": "테스트"}'))

    _patch_transport(monkeypatch, handler)
    adapter = GeminiAdapter(_settings())

    result = await adapter.generate("user prompt", system="system prompt")

    assert captured["url"] == (
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
    )
    assert captured["api_key"] == "test-gemini-key"
    payload = captured["payload"]
    assert payload["contents"] == [{"parts": [{"text": "user prompt"}]}]
    assert payload["systemInstruction"] == {"parts": [{"text": "system prompt"}]}
    # JSON 모드 강제 — 프롬프트 지시 미준수로 인한 구조화 파싱 강등을 줄인다
    assert payload["generationConfig"]["responseMimeType"] == "application/json"
    assert result.report == '{"riskSummary": "테스트"}'
    assert result.model == "gemini-2.5-flash"


async def test_generate_without_system_omits_system_instruction(monkeypatch):
    captured: dict = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["payload"] = json.loads(request.content)
        return httpx.Response(200, json=_gemini_ok_body("ok"))

    _patch_transport(monkeypatch, handler)

    await GeminiAdapter(_settings()).generate("prompt only")

    assert "systemInstruction" not in captured["payload"]


async def test_generate_concatenates_multiple_parts(monkeypatch):
    body = {"candidates": [{"content": {"parts": [{"text": "앞부분 "}, {"text": "뒷부분"}]}}]}
    _patch_transport(monkeypatch, lambda request: httpx.Response(200, json=body))

    result = await GeminiAdapter(_settings()).generate("prompt")

    assert result.report == "앞부분 뒷부분"


async def test_http_error_raises_adapter_error(monkeypatch):
    _patch_transport(
        monkeypatch,
        lambda request: httpx.Response(429, json={"error": {"message": "quota exceeded"}}),
    )

    with pytest.raises(ModelAdapterError, match="Gemini request failed"):
        await GeminiAdapter(_settings()).generate("prompt")


async def test_empty_candidates_raises_adapter_error(monkeypatch):
    _patch_transport(monkeypatch, lambda request: httpx.Response(200, json={"candidates": []}))

    with pytest.raises(ModelAdapterError, match="Unexpected Gemini response shape"):
        await GeminiAdapter(_settings()).generate("prompt")


async def test_empty_text_raises_adapter_error(monkeypatch):
    _patch_transport(
        monkeypatch, lambda request: httpx.Response(200, json=_gemini_ok_body("   "))
    )

    with pytest.raises(ModelAdapterError, match="empty report"):
        await GeminiAdapter(_settings()).generate("prompt")
