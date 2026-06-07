package com.chainwatch.backend.analysis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainwatch.ai")
public record AiAnalysisProperties(
        boolean enabled,
        String provider,
        String model,
        String baseUrl,
        String analyzePath
) {
}
