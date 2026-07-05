"""Claude adapter backed by the official Anthropic SDK."""

from anthropic import AsyncAnthropic

from app.adapters.base import AdapterResult, ModelAdapter, ModelAdapterError
from app.config import Settings
from app.constants import PROVIDER_CLAUDE


class ClaudeAdapter(ModelAdapter):
    name = PROVIDER_CLAUDE

    def __init__(self, settings: Settings):
        client_kwargs = {}
        if settings.anthropic_api_key:
            client_kwargs["api_key"] = settings.anthropic_api_key
        self._client = AsyncAnthropic(**client_kwargs)
        self._model = settings.claude_model
        self._max_tokens = settings.claude_max_tokens

    async def generate(self, prompt: str) -> AdapterResult:
        try:
            response = await self._client.messages.create(
                model=self._model,
                max_tokens=self._max_tokens,
                thinking={"type": "adaptive"},
                messages=[{"role": "user", "content": prompt}],
            )
        except Exception as error:
            raise ModelAdapterError(f"Claude request failed: {error}") from error

        if response.stop_reason == "refusal":
            raise ModelAdapterError("Claude declined the request (stop_reason=refusal)")

        report = "".join(block.text for block in response.content if block.type == "text").strip()
        if not report:
            raise ModelAdapterError("Claude returned an empty report")

        return AdapterResult(report=report, raw_response=response.to_json(), model=response.model)
