"""Model adapter registry construction."""

import os

from app.adapters.base import ModelAdapter
from app.adapters.claude_adapter import ClaudeAdapter
from app.adapters.gemini_adapter import GeminiAdapter
from app.adapters.mock_adapter import MockAdapter
from app.adapters.openai_compatible_adapter import OpenAiCompatibleAdapter
from app.config import Settings
from app.constants import PROVIDER_HERMES, PROVIDER_LMSTUDIO


def build_adapters(settings: Settings) -> dict[str, ModelAdapter]:
    """Build only the adapters whose credentials/endpoints are configured.

    The mock adapter is always registered so the pipeline stays testable
    without external credentials.
    """
    adapters: dict[str, ModelAdapter] = {}

    if settings.anthropic_api_key or os.environ.get("ANTHROPIC_API_KEY"):
        claude = ClaudeAdapter(settings)
        adapters[claude.name] = claude

    if settings.gemini_api_key:
        gemini = GeminiAdapter(settings)
        adapters[gemini.name] = gemini

    if settings.lmstudio_base_url:
        adapters[PROVIDER_LMSTUDIO] = OpenAiCompatibleAdapter(
            name=PROVIDER_LMSTUDIO,
            base_url=settings.lmstudio_base_url,
            model=settings.lmstudio_model,
            timeout_seconds=settings.http_timeout_seconds,
        )

    if settings.hermes_base_url:
        adapters[PROVIDER_HERMES] = OpenAiCompatibleAdapter(
            name=PROVIDER_HERMES,
            base_url=settings.hermes_base_url,
            model=settings.hermes_model,
            timeout_seconds=settings.http_timeout_seconds,
        )

    mock = MockAdapter()
    adapters[mock.name] = mock

    return adapters
