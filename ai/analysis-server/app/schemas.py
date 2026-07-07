"""Request/response schemas matching the backend FastApiAiAnalysisClient contract.

The Spring backend serializes record fields in camelCase and expects a
camelCase response body with at least `report` and `rawResponse`.

v3 adds optional structured analysis fields. They are additive and nullable so
older clients that only read `report`/`rawResponse` keep working unchanged.

Conventions (documented for consumers):
- `confidence` is one of "low" | "medium" | "high".
- `escalation_level` is one of "none" | "monitor" | "escalate" | "urgent".
- `structured=False` means the LLM output could not be parsed into the
  structured schema and only the free-form `report` text is reliable.
"""

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

CONFIDENCE_LEVELS = ("low", "medium", "high")
ESCALATION_LEVELS = ("none", "monitor", "escalate", "urgent")


class CamelCaseModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class AnalysisRequest(CamelCaseModel):
    detection_event_id: int | None = None
    provider: str | None = None
    model: str | None = None
    event_type: str | None = None
    risk_level: str | None = None
    risk_score: int | None = None
    wallet_address: str | None = None
    tx_hash: str | None = None
    summary: str = Field(min_length=1)


class EvidenceItem(CamelCaseModel):
    """A single grounded fact: where it came from and what it says."""

    source: str
    fact: str


class AnalysisResponse(CamelCaseModel):
    report: str
    raw_response: str
    provider: str
    model: str
    prompt_version: str
    # --- structured fields (v3, additive) -------------------------------
    # False when the LLM output could not be parsed into the structured
    # schema; in that case `report` carries the raw text and every field
    # below is empty/None.
    structured: bool = False
    risk_summary: str | None = None
    evidence: list[EvidenceItem] = Field(default_factory=list)
    possible_scenarios: list[str] = Field(default_factory=list)
    recommended_actions: list[str] = Field(default_factory=list)
    confidence: str | None = None
    false_positive_factors: list[str] = Field(default_factory=list)
    escalation_level: str | None = None


class ErrorResponse(CamelCaseModel):
    code: str
    message: str


class ProvidersResponse(CamelCaseModel):
    default_provider: str
    available_providers: list[str]
