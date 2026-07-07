"""Parse LLM output into the structured analysis schema.

The model is instructed (prompts v3) to output a single JSON object. Real
providers wrap JSON in markdown fences or add stray prose, so extraction is
lenient; validation is strict. Any failure returns None so the caller can
degrade gracefully to a text-only report (structured=False) instead of
erroring the whole analysis.
"""

import json
import logging
import re

from pydantic import Field, ValidationError, field_validator

from app.schemas import CONFIDENCE_LEVELS, ESCALATION_LEVELS, CamelCaseModel, EvidenceItem

logger = logging.getLogger(__name__)

_CODE_FENCE_RE = re.compile(r"```(?:json)?\s*(\{.*\})\s*```", re.DOTALL)

_CONFIDENCE_SYNONYMS = {
    "낮음": "low",
    "중간": "medium",
    "높음": "high",
    "med": "medium",
    "mid": "medium",
}

_ESCALATION_SYNONYMS = {
    "없음": "none",
    "관찰": "monitor",
    "모니터링": "monitor",
    "monitoring": "monitor",
    "에스컬레이션": "escalate",
    "escalation": "escalate",
    "긴급": "urgent",
    "critical": "urgent",
    "immediate": "urgent",
}


class StructuredReport(CamelCaseModel):
    """Validated shape of the JSON the model is asked to produce."""

    report: str | None = None
    risk_summary: str
    evidence: list[EvidenceItem] = Field(default_factory=list)
    possible_scenarios: list[str] = Field(default_factory=list)
    recommended_actions: list[str] = Field(default_factory=list)
    confidence: str
    false_positive_factors: list[str] = Field(default_factory=list)
    escalation_level: str

    @field_validator("confidence", mode="before")
    @classmethod
    def normalize_confidence(cls, value: object) -> object:
        return _normalize_level(value, CONFIDENCE_LEVELS, _CONFIDENCE_SYNONYMS)

    @field_validator("escalation_level", mode="before")
    @classmethod
    def normalize_escalation(cls, value: object) -> object:
        return _normalize_level(value, ESCALATION_LEVELS, _ESCALATION_SYNONYMS)


def _normalize_level(value: object, allowed: tuple[str, ...], synonyms: dict[str, str]) -> object:
    if not isinstance(value, str):
        raise ValueError(f"expected a string, got {type(value).__name__}")
    normalized = value.strip().lower()
    normalized = synonyms.get(normalized, normalized)
    if normalized not in allowed:
        raise ValueError(f"'{value}' is not one of {allowed}")
    return normalized


def _extract_json_candidate(text: str) -> str | None:
    fence_match = _CODE_FENCE_RE.search(text)
    if fence_match:
        return fence_match.group(1)
    start = text.find("{")
    end = text.rfind("}")
    if start == -1 or end <= start:
        return None
    return text[start : end + 1]


def parse_structured_report(text: str) -> StructuredReport | None:
    """Best-effort parse of LLM output; returns None on any failure."""
    candidate = _extract_json_candidate(text)
    if candidate is None:
        return None

    try:
        data = json.loads(candidate)
    except json.JSONDecodeError:
        logger.warning("structured report parse failed: invalid JSON")
        return None

    if not isinstance(data, dict):
        logger.warning("structured report parse failed: top-level JSON is not an object")
        return None

    try:
        return StructuredReport.model_validate(data)
    except ValidationError as error:
        logger.warning("structured report parse failed: schema validation error: %s", error)
        return None
