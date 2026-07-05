"""Deterministic mock adapter for local development and tests."""

import json

from app.adapters.base import AdapterResult, ModelAdapter
from app.constants import PROVIDER_MOCK

_MOCK_MODEL = "mock-analyzer"


class MockAdapter(ModelAdapter):
    name = PROVIDER_MOCK

    async def generate(self, prompt: str) -> AdapterResult:
        report = (
            "[Mock 분석 리포트]\n"
            "이 리포트는 외부 LLM 없이 생성된 모의 응답입니다. "
            "탐지 이벤트의 상세 내용을 검토하고 지갑 주소의 최근 활동을 확인하세요.\n"
            "권장 조치: 1) 이벤트 상세 확인 2) 관련 트랜잭션 추적 3) 필요 시 워치리스트 등록"
        )
        raw = json.dumps({"provider": PROVIDER_MOCK, "prompt_length": len(prompt)}, ensure_ascii=False)
        return AdapterResult(report=report, raw_response=raw, model=_MOCK_MODEL)
