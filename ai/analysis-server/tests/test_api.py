from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from app.services.analysis_service import AnalysisService
from tests.conftest import StubAdapter


def make_client(fallback_chain: str = "mock", adapters: dict | None = None) -> TestClient:
    settings = Settings(
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
    assert body["promptVersion"] == "v1"


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
