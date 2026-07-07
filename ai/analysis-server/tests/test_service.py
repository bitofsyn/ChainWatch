import pytest

from app.prompts import SYSTEM_PROMPT, UNTRUSTED_BLOCK_START
from app.schemas import AnalysisRequest
from app.services.analysis_service import AnalysisFailedError, AnalysisService
from tests.conftest import STRUCTURED_STUB_REPORT, StubAdapter


def make_request(provider: str | None = None) -> AnalysisRequest:
    return AnalysisRequest.model_validate({"summary": "테스트 이벤트", "provider": provider})


async def test_primary_provider_success(settings):
    primary = StubAdapter("primary")
    service = AnalysisService(settings, {"primary": primary})

    response = await service.analyze(make_request())

    assert response.report == "stub report"
    assert response.provider == "primary"
    assert response.prompt_version == "v3"
    assert primary.call_count == 1


async def test_structured_report_is_parsed(settings):
    primary = StubAdapter("primary", report=STRUCTURED_STUB_REPORT)
    service = AnalysisService(settings, {"primary": primary})

    response = await service.analyze(make_request())

    assert response.structured is True
    assert response.risk_summary == "고위험 대량 이체 정황"
    assert response.report == "## 개요\n스텁 구조화 리포트"
    assert response.evidence[0].source == "summary"
    assert response.evidence[0].fact == "고액 이체 탐지"
    assert response.possible_scenarios == ["자금 세탁 의심"]
    assert response.recommended_actions == ["워치리스트 등재"]
    assert response.confidence == "medium"
    assert response.false_positive_factors == ["거래소 내부 이동 가능성"]
    assert response.escalation_level == "escalate"


async def test_unparseable_report_degrades_to_text_only(settings):
    primary = StubAdapter("primary", report="자유 서술 리포트 (JSON 아님)")
    service = AnalysisService(settings, {"primary": primary})

    response = await service.analyze(make_request())

    assert response.structured is False
    assert response.report == "자유 서술 리포트 (JSON 아님)"
    assert response.risk_summary is None
    assert response.evidence == []
    assert response.confidence is None
    assert response.escalation_level is None


async def test_partially_valid_json_degrades_to_text_only(settings):
    # Valid JSON but missing required fields -> graceful degradation, not an error.
    primary = StubAdapter("primary", report='{"riskSummary": "요약만 있음"}')
    service = AnalysisService(settings, {"primary": primary})

    response = await service.analyze(make_request())

    assert response.structured is False
    assert response.report == '{"riskSummary": "요약만 있음"}'


async def test_system_prompt_is_sent_separately_from_event_data(settings):
    primary = StubAdapter("primary")
    service = AnalysisService(settings, {"primary": primary})

    await service.analyze(make_request())

    assert primary.last_system == SYSTEM_PROMPT
    assert UNTRUSTED_BLOCK_START in primary.last_prompt
    # Untrusted event data must never appear in the instruction channel.
    assert "테스트 이벤트" not in primary.last_system
    assert "테스트 이벤트" in primary.last_prompt


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
