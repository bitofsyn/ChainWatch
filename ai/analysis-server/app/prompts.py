"""Prompt template for detection event analysis reports."""

from app.schemas import AnalysisRequest

PROMPT_VERSION = "v1"

_UNKNOWN = "N/A"

_TEMPLATE = """당신은 블록체인 온체인 이상거래 분석 전문가입니다.
아래 탐지 이벤트를 분석하여 운영자가 읽을 수 있는 한국어 리포트를 작성하세요.

[탐지 이벤트]
- 이벤트 ID: {detection_event_id}
- 이벤트 유형: {event_type}
- 위험 등급: {risk_level}
- 위험 점수: {risk_score}
- 지갑 주소: {wallet_address}
- 트랜잭션 해시: {tx_hash}
- 탐지 요약: {summary}

[작성 지침]
1. 첫 문단에 이 이벤트가 무엇이며 왜 탐지되었는지 2~3문장으로 요약하세요.
2. 위험 등급과 점수의 의미를 해석하고, 가능한 위험 시나리오를 설명하세요.
3. 운영자가 취해야 할 권장 조치를 목록으로 제시하세요.
4. 근거 없는 추측은 하지 말고, 주어진 데이터에 기반해 작성하세요.
5. 전체 분량은 500자 이내로 간결하게 작성하세요.
"""


def build_analysis_prompt(request: AnalysisRequest) -> str:
    return _TEMPLATE.format(
        detection_event_id=request.detection_event_id if request.detection_event_id is not None else _UNKNOWN,
        event_type=request.event_type or _UNKNOWN,
        risk_level=request.risk_level or _UNKNOWN,
        risk_score=request.risk_score if request.risk_score is not None else _UNKNOWN,
        wallet_address=request.wallet_address or _UNKNOWN,
        tx_hash=request.tx_hash or _UNKNOWN,
        summary=request.summary,
    )
