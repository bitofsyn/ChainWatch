"""Deterministic mock adapter for local development and tests."""

import json

from app.adapters.base import AdapterResult, ModelAdapter
from app.constants import PROVIDER_MOCK

_MOCK_MODEL = "mock-analyzer"


class MockAdapter(ModelAdapter):
    name = PROVIDER_MOCK

    async def generate(self, prompt: str, system: str | None = None) -> AdapterResult:
        # Emit the same structured JSON contract as real providers (prompts v3)
        # so the end-to-end structured pipeline is exercised without an LLM.
        structured = {
            "riskSummary": "모의 분석 응답입니다. 외부 LLM 없이 생성된 구조화 리포트입니다.",
            "report": (
                "[Mock 분석 리포트]\n"
                "이 리포트는 외부 LLM 없이 생성된 모의 응답입니다. "
                "탐지 이벤트의 상세 내용을 검토하고 지갑 주소의 최근 활동을 확인하세요."
            ),
            "evidence": [
                {"source": "prompt", "fact": f"프롬프트 길이 {len(prompt)}자"},
            ],
            "possibleScenarios": ["모의 응답: 실제 LLM 연동 시 데이터 기반 시나리오로 대체됩니다."],
            "recommendedActions": [
                "이벤트 상세 확인",
                "관련 트랜잭션 추적",
                "필요 시 워치리스트 등록",
            ],
            "confidence": "low",
            "falsePositiveFactors": ["모의 응답이므로 실제 위험 평가가 아닙니다."],
            "escalationLevel": "monitor",
        }
        report = json.dumps(structured, ensure_ascii=False)
        raw = json.dumps({"provider": PROVIDER_MOCK, "prompt_length": len(prompt)}, ensure_ascii=False)
        return AdapterResult(report=report, raw_response=raw, model=_MOCK_MODEL)
