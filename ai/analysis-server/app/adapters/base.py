"""Model adapter abstraction."""

from abc import ABC, abstractmethod
from dataclasses import dataclass


class ModelAdapterError(Exception):
    """Raised when an adapter fails to produce a usable report."""


@dataclass(frozen=True)
class AdapterResult:
    report: str
    raw_response: str
    model: str


class ModelAdapter(ABC):
    """Generates an analysis report from a prompt using one model provider."""

    name: str

    @abstractmethod
    async def generate(self, prompt: str, system: str | None = None) -> AdapterResult:
        """Generate a report.

        `prompt` is the user-turn content (may embed untrusted event data in
        delimited blocks); `system` carries trusted instructions only and must
        be sent in the provider's instruction channel when supported.

        Raises ModelAdapterError on any failure.
        """
        raise NotImplementedError
