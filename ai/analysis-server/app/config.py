"""Application settings loaded from environment variables (prefix: CHAINWATCH_AI_)."""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict

from app.constants import PROVIDER_CLAUDE, PROVIDER_GEMINI, PROVIDER_HERMES, PROVIDER_LMSTUDIO


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="CHAINWATCH_AI_", env_file=".env", extra="ignore")

    app_name: str = "chainwatch-ai-analysis-server"

    default_provider: str = PROVIDER_CLAUDE
    # mock은 기본 폴백 체인에서 제외한다: 실제 LLM이 모두 실패하면 모의 리포트를 지어내는 대신
    # 분석 실패(FAILED)로 정직하게 기록되게 한다. 테스트/명시적 요청(provider=mock)만 mock 사용.
    fallback_chain: str = ",".join(
        (PROVIDER_CLAUDE, PROVIDER_GEMINI, PROVIDER_LMSTUDIO, PROVIDER_HERMES)
    )
    retry_max_attempts: int = 2
    retry_backoff_seconds: float = 0.5

    # Claude (Anthropic). If empty, the anthropic SDK falls back to ANTHROPIC_API_KEY.
    anthropic_api_key: str = ""
    claude_model: str = "claude-opus-4-8"
    claude_max_tokens: int = 4096

    # Gemini (Google Generative Language API)
    gemini_api_key: str = ""
    gemini_model: str = "gemini-flash-latest"  # 고정 버전 모델은 신규 계정에서 차단될 수 있어 alias 기본
    gemini_base_url: str = "https://generativelanguage.googleapis.com"
    # 기본 모델이 과부하(503)·한도 초과(429)·미지원(404)이면 순서대로 시도할 예비 모델(쉼표 구분).
    gemini_fallback_models: str = "gemini-flash-lite-latest,gemini-2.5-flash"
    # 모델 하나당 대기 시간. 과부하 모델이 응답 없이 붙잡고 있어도 예비 모델로 넘어가
    # 백엔드 요청 제한(120초) 안에 끝나도록 짧게 둔다(3개 모델 × 30초 = 90초).
    gemini_timeout_seconds: float = 30.0

    # OpenAI-compatible local servers (LM Studio, Hermes)
    lmstudio_base_url: str = ""
    lmstudio_model: str = "local-model"
    hermes_base_url: str = ""
    hermes_model: str = "hermes"

    http_timeout_seconds: float = 60.0

    # 공개 주소로 배포할 때 백엔드만 분석을 요청할 수 있게 하는 공유 토큰.
    # 비어 있으면 검사하지 않는다(로컬·docker-compose 내부망).
    api_token: str = ""

    def gemini_models(self) -> list[str]:
        models = [self.gemini_model] + self.gemini_fallback_models.split(",")
        return list(dict.fromkeys(m.strip() for m in models if m.strip()))

    def fallback_providers(self) -> list[str]:
        return [name.strip() for name in self.fallback_chain.split(",") if name.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
