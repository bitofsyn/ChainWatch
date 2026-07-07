package com.chainwatch.backend.analysis.client;

import java.util.List;

/**
 * Structured analysis fields returned by the AI analysis server (prompts v3).
 * All fields are optional: when the AI server degrades to a text-only report
 * (structured=false) this object is absent entirely.
 *
 * <p>Conventions: {@code confidence} is one of low|medium|high,
 * {@code escalationLevel} is one of none|monitor|escalate|urgent.
 */
public record AiStructuredAnalysis(
        String riskSummary,
        List<EvidenceItem> evidence,
        List<String> possibleScenarios,
        List<String> recommendedActions,
        String confidence,
        List<String> falsePositiveFactors,
        String escalationLevel
) {
    public record EvidenceItem(
            String source,
            String fact
    ) {
    }
}
