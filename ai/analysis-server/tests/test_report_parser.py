import json

from app.report_parser import parse_structured_report

VALID_PAYLOAD = {
    "riskSummary": "고위험 대량 이체 정황",
    "report": "## 개요\n상세 리포트 본문",
    "evidence": [{"source": "riskScore", "fact": "위험 점수 85"}],
    "possibleScenarios": ["자금 세탁 의심 이동"],
    "recommendedActions": ["지갑 주소 워치리스트 등재"],
    "confidence": "high",
    "falsePositiveFactors": ["거래소 내부 리밸런싱 가능성"],
    "escalationLevel": "escalate",
}


def test_parses_plain_json():
    parsed = parse_structured_report(json.dumps(VALID_PAYLOAD, ensure_ascii=False))

    assert parsed is not None
    assert parsed.risk_summary == "고위험 대량 이체 정황"
    assert parsed.report == "## 개요\n상세 리포트 본문"
    assert parsed.evidence[0].source == "riskScore"
    assert parsed.evidence[0].fact == "위험 점수 85"
    assert parsed.confidence == "high"
    assert parsed.escalation_level == "escalate"


def test_parses_json_inside_markdown_fence():
    text = "```json\n" + json.dumps(VALID_PAYLOAD, ensure_ascii=False) + "\n```"
    parsed = parse_structured_report(text)
    assert parsed is not None
    assert parsed.confidence == "high"


def test_parses_json_with_surrounding_prose():
    text = "다음은 분석 결과입니다.\n" + json.dumps(VALID_PAYLOAD, ensure_ascii=False) + "\n이상입니다."
    parsed = parse_structured_report(text)
    assert parsed is not None
    assert parsed.risk_summary == "고위험 대량 이체 정황"


def test_normalizes_level_synonyms_and_case():
    payload = dict(VALID_PAYLOAD, confidence="HIGH", escalationLevel="모니터링")
    parsed = parse_structured_report(json.dumps(payload, ensure_ascii=False))
    assert parsed is not None
    assert parsed.confidence == "high"
    assert parsed.escalation_level == "monitor"


def test_invalid_json_returns_none():
    assert parse_structured_report("그냥 자유 서술 리포트입니다. JSON 없음.") is None
    assert parse_structured_report("{invalid json}") is None


def test_non_object_json_returns_none():
    assert parse_structured_report('["not", "an", "object"]') is None


def test_missing_required_fields_returns_none():
    payload = {"report": "요약 필드가 없는 응답"}
    assert parse_structured_report(json.dumps(payload, ensure_ascii=False)) is None


def test_invalid_level_value_returns_none():
    payload = dict(VALID_PAYLOAD, confidence="확실함")
    assert parse_structured_report(json.dumps(payload, ensure_ascii=False)) is None

    payload = dict(VALID_PAYLOAD, escalationLevel=3)
    assert parse_structured_report(json.dumps(payload, ensure_ascii=False)) is None


def test_malformed_evidence_items_return_none():
    payload = dict(VALID_PAYLOAD, evidence=[{"source": "summary"}])
    assert parse_structured_report(json.dumps(payload, ensure_ascii=False)) is None


def test_optional_lists_default_to_empty():
    payload = {
        "riskSummary": "요약",
        "confidence": "low",
        "escalationLevel": "none",
    }
    parsed = parse_structured_report(json.dumps(payload, ensure_ascii=False))
    assert parsed is not None
    assert parsed.evidence == []
    assert parsed.possible_scenarios == []
    assert parsed.recommended_actions == []
    assert parsed.false_positive_factors == []
