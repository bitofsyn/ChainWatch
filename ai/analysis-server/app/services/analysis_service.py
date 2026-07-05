"""Analysis orchestration: provider selection, per-adapter retry, and fallback chain."""

import asyncio
import logging

from app.adapters.base import ModelAdapter, ModelAdapterError
from app.config import Settings
from app.prompts import PROMPT_VERSION, build_analysis_prompt
from app.schemas import AnalysisRequest, AnalysisResponse

logger = logging.getLogger(__name__)


class AnalysisFailedError(Exception):
    """Raised when every configured provider failed to produce a report."""

    def __init__(self, attempted_providers: list[str], last_error: Exception | None):
        self.attempted_providers = attempted_providers
        self.last_error = last_error
        super().__init__(
            f"All providers failed: {attempted_providers} (last error: {last_error})"
        )


class AnalysisService:
    def __init__(self, settings: Settings, adapters: dict[str, ModelAdapter]):
        self._settings = settings
        self._adapters = adapters
        self._last_errors: dict[str, Exception] = {}

    def available_providers(self) -> list[str]:
        return list(self._adapters.keys())

    def _provider_order(self, requested_provider: str | None) -> list[str]:
        """Requested provider first, then the configured fallback chain (deduplicated)."""
        order: list[str] = []
        candidates = [requested_provider or self._settings.default_provider]
        candidates.extend(self._settings.fallback_providers())
        for name in candidates:
            if name in self._adapters and name not in order:
                order.append(name)
        return order

    async def analyze(self, request: AnalysisRequest) -> AnalysisResponse:
        prompt = build_analysis_prompt(request)
        order = self._provider_order(request.provider)
        last_error: Exception | None = None

        for provider_name in order:
            adapter = self._adapters[provider_name]
            result = await self._generate_with_retry(adapter, prompt)
            if result is not None:
                logger.info(
                    "analysis completed | eventId=%s provider=%s model=%s",
                    request.detection_event_id, provider_name, result.model,
                )
                return AnalysisResponse(
                    report=result.report,
                    raw_response=result.raw_response,
                    provider=provider_name,
                    model=result.model,
                    prompt_version=PROMPT_VERSION,
                )
            last_error = self._last_errors.get(provider_name)

        raise AnalysisFailedError(order, last_error)

    async def _generate_with_retry(self, adapter: ModelAdapter, prompt: str):
        max_attempts = max(1, self._settings.retry_max_attempts)
        for attempt in range(1, max_attempts + 1):
            try:
                return await adapter.generate(prompt)
            except ModelAdapterError as error:
                self._last_errors[adapter.name] = error
                logger.warning(
                    "analysis attempt failed | provider=%s attempt=%d/%d error=%s",
                    adapter.name, attempt, max_attempts, error,
                )
                if attempt < max_attempts:
                    await asyncio.sleep(self._settings.retry_backoff_seconds * attempt)
        return None
