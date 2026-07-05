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
    async def generate(self, prompt: str) -> AdapterResult:
        """Generate a report. Raises ModelAdapterError on any failure."""
        raise NotImplementedError
