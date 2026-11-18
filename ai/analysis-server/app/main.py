"""FastAPI application entry point for the ChainWatch AI Analysis Server."""

import hmac
import logging

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.responses import JSONResponse

from app.adapters import build_adapters
from app.config import Settings, get_settings
from app.schemas import AnalysisRequest, AnalysisResponse, ErrorResponse, ProvidersResponse
from app.services.analysis_service import AnalysisFailedError, AnalysisService

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)

ANALYZE_PATH = "/api/v1/analyze"
PROVIDERS_PATH = "/api/v1/providers"
HEALTH_PATH = "/health"

TOKEN_HEADER = "X-ChainWatch-AI-Token"

ERROR_CODE_ANALYSIS_FAILED = "ANALYSIS_FAILED"


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or get_settings()
    app = FastAPI(title=settings.app_name)

    async def require_token(
        token: str | None = Header(default=None, alias=TOKEN_HEADER),
    ) -> None:
        """api_token이 설정돼 있으면 같은 값을 보낸 요청만 통과시킨다(LLM 키 사용량 보호)."""
        expected = settings.api_token
        if expected and not hmac.compare_digest(token or "", expected):
            raise HTTPException(status_code=401, detail="Invalid or missing AI server token")
    app.state.analysis_service = AnalysisService(settings, build_adapters(settings))
    app.state.settings = settings

    @app.exception_handler(AnalysisFailedError)
    async def handle_analysis_failed(_: Request, error: AnalysisFailedError) -> JSONResponse:
        body = ErrorResponse(code=ERROR_CODE_ANALYSIS_FAILED, message=str(error))
        return JSONResponse(status_code=502, content=body.model_dump(by_alias=True))

    @app.get(HEALTH_PATH)
    async def health() -> dict[str, str]:
        return {"status": "UP"}

    @app.get(
        PROVIDERS_PATH,
        response_model=ProvidersResponse,
        response_model_by_alias=True,
        dependencies=[Depends(require_token)],
    )
    async def providers(request: Request) -> ProvidersResponse:
        service: AnalysisService = request.app.state.analysis_service
        return ProvidersResponse(
            default_provider=request.app.state.settings.default_provider,
            available_providers=service.available_providers(),
        )

    @app.post(
        ANALYZE_PATH,
        response_model=AnalysisResponse,
        response_model_by_alias=True,
        dependencies=[Depends(require_token)],
    )
    async def analyze(body: AnalysisRequest, request: Request) -> AnalysisResponse:
        service: AnalysisService = request.app.state.analysis_service
        return await service.analyze(body)

    return app


app = create_app()
