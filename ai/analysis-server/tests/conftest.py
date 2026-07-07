import json

import pytest

from app.adapters.base import AdapterResult, ModelAdapter, ModelAdapterError
from app.config import Settings

STRUCTURED_STUB_REPORT = json.dumps(
    {
        "riskSummary": "고위험 대량 이체 정황",
        "report": "## 개요\n스텁 구조화 리포트",
        "evidence": [{"source": "summary", "fact": "고액 이체 탐지"}],
        "possibleScenarios": ["자금 세탁 의심"],
        "recommendedActions": ["워치리스트 등재"],
        "confidence": "medium",
        "falsePositiveFactors": ["거래소 내부 이동 가능성"],
        "escalationLevel": "escalate",
    },
    ensure_ascii=False,
)


class StubAdapter(ModelAdapter):
    """Test adapter that succeeds after a configurable number of failures."""

    def __init__(self, name: str, fail_times: int = 0, report: str = "stub report"):
        self.name = name
        self.fail_times = fail_times
        self.report = report
        self.call_count = 0
        self.last_prompt: str | None = None
        self.last_system: str | None = None

    async def generate(self, prompt: str, system: str | None = None) -> AdapterResult:
        self.call_count += 1
        self.last_prompt = prompt
        self.last_system = system
        if self.call_count <= self.fail_times:
            raise ModelAdapterError(f"{self.name} simulated failure #{self.call_count}")
        return AdapterResult(report=self.report, raw_response="{}", model=f"{self.name}-model")


@pytest.fixture
def settings() -> Settings:
    return Settings(
        default_provider="primary",
        fallback_chain="primary,secondary,mock",
        retry_max_attempts=2,
        retry_backoff_seconds=0.0,
    )
