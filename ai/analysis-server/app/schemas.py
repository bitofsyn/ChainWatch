"""Request/response schemas matching the backend FastApiAiAnalysisClient contract.

The Spring backend serializes record fields in camelCase and expects a
camelCase response body with at least `report` and `rawResponse`.
"""

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


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


class AnalysisResponse(CamelCaseModel):
    report: str
    raw_response: str
    provider: str
    model: str
    prompt_version: str


class ErrorResponse(CamelCaseModel):
    code: str
    message: str


class ProvidersResponse(CamelCaseModel):
    default_provider: str
    available_providers: list[str]
