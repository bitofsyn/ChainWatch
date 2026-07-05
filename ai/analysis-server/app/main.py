"""FastAPI application entry point for the ChainWatch AI Analysis Server."""

import logging

from fastapi import FastAPI, Request
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

ERROR_CODE_ANALYSIS_FAILED = "ANALYSIS_FAILED"


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or get_settings()
    app = FastAPI(title=settings.app_name)
    app.state.analysis_service = AnalysisService(settings, build_adapters(settings))
    app.state.settings = settings

    @app.exception_handler(AnalysisFailedError)
    async def handle_analysis_failed(_: Request, error: AnalysisFailedError) -> JSONResponse:
        body = ErrorResponse(code=ERROR_CODE_ANALYSIS_FAILED, message=str(error))
        return JSONResponse(status_code=502, content=body.model_dump(by_alias=True))

    @app.get(HEALTH_PATH)
    async def health() -> dict[str, str]:
        return {"status": "UP"}

    @app.get(PROVIDERS_PATH, response_model=ProvidersResponse, response_model_by_alias=True)
    async def providers(request: Request) -> ProvidersResponse:
        service: AnalysisService = request.app.state.analysis_service
        return ProvidersResponse(
            default_provider=request.app.state.settings.default_provider,
            available_providers=service.available_providers(),
        )

    @app.post(ANALYZE_PATH, response_model=AnalysisResponse, response_model_by_alias=True)
    async def analyze(body: AnalysisRequest, request: Request) -> AnalysisResponse:
        service: AnalysisService = request.app.state.analysis_service
        return await service.analyze(body)

    return app


app = create_app()
