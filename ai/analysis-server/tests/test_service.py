import pytest

from app.schemas import AnalysisRequest
from app.services.analysis_service import AnalysisFailedError, AnalysisService
from tests.conftest import StubAdapter


def make_request(provider: str | None = None) -> AnalysisRequest:
    return AnalysisRequest.model_validate({"summary": "테스트 이벤트", "provider": provider})


async def test_primary_provider_success(settings):
    primary = StubAdapter("primary")
    service = AnalysisService(settings, {"primary": primary})

    response = await service.analyze(make_request())

    assert response.report == "stub report"
    assert response.provider == "primary"
    assert response.prompt_version == "v1"
    assert primary.call_count == 1


async def test_retry_within_same_provider(settings):
    primary = StubAdapter("primary", fail_times=1)
    service = AnalysisService(settings, {"primary": primary})

    response = await service.analyze(make_request())

    assert response.provider == "primary"
    assert primary.call_count == 2


async def test_fallback_to_next_provider(settings):
    primary = StubAdapter("primary", fail_times=10)
    secondary = StubAdapter("secondary", report="secondary report")
    service = AnalysisService(settings, {"primary": primary, "secondary": secondary})

    response = await service.analyze(make_request())

    assert response.provider == "secondary"
    assert response.report == "secondary report"
    assert primary.call_count == settings.retry_max_attempts


async def test_requested_provider_takes_priority(settings):
    primary = StubAdapter("primary")
    secondary = StubAdapter("secondary", report="secondary report")
    service = AnalysisService(settings, {"primary": primary, "secondary": secondary})

    response = await service.analyze(make_request(provider="secondary"))

    assert response.provider == "secondary"
    assert primary.call_count == 0


async def test_unknown_requested_provider_falls_back_to_chain(settings):
    primary = StubAdapter("primary")
    service = AnalysisService(settings, {"primary": primary})

    response = await service.analyze(make_request(provider="does-not-exist"))

    assert response.provider == "primary"


async def test_all_providers_fail_raises(settings):
    primary = StubAdapter("primary", fail_times=10)
    secondary = StubAdapter("secondary", fail_times=10)
    service = AnalysisService(settings, {"primary": primary, "secondary": secondary})

    with pytest.raises(AnalysisFailedError) as exc_info:
        await service.analyze(make_request())

    assert exc_info.value.attempted_providers == ["primary", "secondary"]
