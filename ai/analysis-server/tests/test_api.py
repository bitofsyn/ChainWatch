from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from app.services.analysis_service import AnalysisService
from tests.conftest import StubAdapter


def make_client(
    fallback_chain: str = "mock", adapters: dict | None = None, api_token: str = ""
) -> TestClient:
    settings = Settings(
        api_token=api_token,
        default_provider="mock",
        fallback_chain=fallback_chain,
        retry_max_attempts=1,
        retry_backoff_seconds=0.0,
        anthropic_api_key="",
        gemini_api_key="",
        lmstudio_base_url="",
        hermes_base_url="",
    )
    app = create_app(settings)
    if adapters is not None:
        app.state.analysis_service = AnalysisService(settings, adapters)
    return TestClient(app)


ANALYZE_BODY = {
    "detectionEventId": 1,
    "provider": None,
    "model": None,
    "eventType": "LARGE_TRANSFER",
    "riskLevel": "HIGH",
    "riskScore": 90,
    "walletAddress": "0xabc",
    "txHash": "0xdef",
    "summary": "고액 이체 탐지",
}


def test_health():
    client = make_client()
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "UP"}


def test_analyze_returns_camel_case_contract():
    client = make_client()
    response = client.post("/api/v1/analyze", json=ANALYZE_BODY)

    assert response.status_code == 200
    body = response.json()
    assert body["report"]
    assert body["rawResponse"]
    assert body["provider"] == "mock"
    assert body["promptVersion"] == "v3"


def test_analyze_returns_structured_fields_in_camel_case():
    client = make_client()
    response = client.post("/api/v1/analyze", json=ANALYZE_BODY)

    assert response.status_code == 200
    body = response.json()
    assert body["structured"] is True
    assert body["riskSummary"]
    assert isinstance(body["evidence"], list)
    assert body["evidence"][0].keys() == {"source", "fact"}
    assert isinstance(body["possibleScenarios"], list)
    assert isinstance(body["recommendedActions"], list)
    assert body["confidence"] in ("low", "medium", "high")
    assert isinstance(body["falsePositiveFactors"], list)
    assert body["escalationLevel"] in ("none", "monitor", "escalate", "urgent")


def test_analyze_degrades_when_provider_returns_unstructured_text():
    plain = StubAdapter("mock", report="자유 서술 리포트")
    client = make_client(adapters={"mock": plain})
    response = client.post("/api/v1/analyze", json=ANALYZE_BODY)

    assert response.status_code == 200
    body = response.json()
    assert body["structured"] is False
    assert body["report"] == "자유 서술 리포트"
    assert body["riskSummary"] is None
    assert body["evidence"] == []
    assert body["confidence"] is None
    assert body["escalationLevel"] is None


def test_analyze_missing_summary_returns_422():
    invalid = dict(ANALYZE_BODY)
    del invalid["summary"]
    client = make_client()
    response = client.post("/api/v1/analyze", json=invalid)
    assert response.status_code == 422


def test_analyze_all_providers_fail_returns_502():
    failing = StubAdapter("mock", fail_times=10)
    client = make_client(adapters={"mock": failing})
    response = client.post("/api/v1/analyze", json=ANALYZE_BODY)

    assert response.status_code == 502
    body = response.json()
    assert body["code"] == "ANALYSIS_FAILED"


def test_providers_endpoint_lists_mock():
    client = make_client()
    response = client.get("/api/v1/providers")
    assert response.status_code == 200
    body = response.json()
    assert "mock" in body["availableProviders"]
    assert body["defaultProvider"] == "mock"


def test_analyze_rejects_missing_or_wrong_token_when_configured():
    client = make_client(api_token="secret-token")

    assert client.post("/api/v1/analyze", json=ANALYZE_BODY).status_code == 401
    wrong = client.post(
        "/api/v1/analyze", json=ANALYZE_BODY, headers={"X-ChainWatch-AI-Token": "nope"}
    )
    assert wrong.status_code == 401
    assert client.get("/api/v1/providers").status_code == 401


def test_analyze_accepts_matching_token_and_health_stays_open():
    client = make_client(api_token="secret-token")

    ok = client.post(
        "/api/v1/analyze", json=ANALYZE_BODY, headers={"X-ChainWatch-AI-Token": "secret-token"}
    )
    assert ok.status_code == 200
    assert client.get("/health").status_code == 200
