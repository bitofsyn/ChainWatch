"""Application settings loaded from environment variables (prefix: CHAINWATCH_AI_)."""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict

from app.constants import PROVIDER_CLAUDE, PROVIDER_GEMINI, PROVIDER_HERMES, PROVIDER_LMSTUDIO, PROVIDER_MOCK


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="CHAINWATCH_AI_", env_file=".env", extra="ignore")

    app_name: str = "chainwatch-ai-analysis-server"

    default_provider: str = PROVIDER_CLAUDE
    fallback_chain: str = ",".join(
        (PROVIDER_CLAUDE, PROVIDER_GEMINI, PROVIDER_LMSTUDIO, PROVIDER_HERMES, PROVIDER_MOCK)
    )
    retry_max_attempts: int = 2
    retry_backoff_seconds: float = 0.5

    # Claude (Anthropic). If empty, the anthropic SDK falls back to ANTHROPIC_API_KEY.
    anthropic_api_key: str = ""
    claude_model: str = "claude-opus-4-8"
    claude_max_tokens: int = 4096

    # Gemini (Google Generative Language API)
    gemini_api_key: str = ""
    gemini_model: str = "gemini-2.5-flash"
    gemini_base_url: str = "https://generativelanguage.googleapis.com"

    # OpenAI-compatible local servers (LM Studio, Hermes)
    lmstudio_base_url: str = ""
    lmstudio_model: str = "local-model"
    hermes_base_url: str = ""
    hermes_model: str = "hermes"

    http_timeout_seconds: float = 60.0

    def fallback_providers(self) -> list[str]:
        return [name.strip() for name in self.fallback_chain.split(",") if name.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
