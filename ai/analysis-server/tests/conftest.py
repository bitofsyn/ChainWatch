import pytest

from app.adapters.base import AdapterResult, ModelAdapter, ModelAdapterError
from app.config import Settings


class StubAdapter(ModelAdapter):
    """Test adapter that succeeds after a configurable number of failures."""

    def __init__(self, name: str, fail_times: int = 0, report: str = "stub report"):
        self.name = name
        self.fail_times = fail_times
        self.report = report
        self.call_count = 0

    async def generate(self, prompt: str) -> AdapterResult:
        self.call_count += 1
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
