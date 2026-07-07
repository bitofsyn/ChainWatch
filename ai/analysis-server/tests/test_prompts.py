from app.prompts import PROMPT_VERSION, build_analysis_prompt
from app.schemas import AnalysisRequest


def make_request(**overrides) -> AnalysisRequest:
    data = {
        "detectionEventId": 42,
        "eventType": "LARGE_TRANSFER",
        "riskLevel": "HIGH",
        "riskScore": 85,
        "walletAddress": "0xabc",
        "txHash": "0xdef",
        "summary": "고액 이체 탐지",
    }
    data.update(overrides)
    return AnalysisRequest.model_validate(data)


def test_prompt_contains_event_fields():
    prompt = build_analysis_prompt(make_request())
    assert "42" in prompt
    assert "LARGE_TRANSFER" in prompt
    assert "HIGH" in prompt
    assert "85" in prompt
    assert "0xabc" in prompt
    assert "0xdef" in prompt
    assert "고액 이체 탐지" in prompt


def test_prompt_handles_missing_optional_fields():
    request = AnalysisRequest.model_validate({"summary": "요약만 존재"})
    prompt = build_analysis_prompt(request)
    assert "N/A" in prompt
    assert "요약만 존재" in prompt


def test_prompt_version_defined():
    assert PROMPT_VERSION == "v2"


def test_prompt_contains_injection_guard():
    prompt = build_analysis_prompt(make_request())
    assert "<event_data>" in prompt
    assert "</event_data>" in prompt
    assert "신뢰할 수 없는 데이터" in prompt


def test_prompt_requires_structured_sections():
    prompt = build_analysis_prompt(make_request())
    for section in ["## 개요", "## 위험 해석", "## 오탐 가능성", "## 신뢰도", "## 권장 대응", "## 추가 확인 사항"]:
        assert section in prompt
