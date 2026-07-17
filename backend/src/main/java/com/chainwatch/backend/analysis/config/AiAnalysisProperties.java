package com.chainwatch.backend.analysis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainwatch.ai")
public record AiAnalysisProperties(
        boolean enabled,
        String provider,
        String model,
        String baseUrl,
        String analyzePath,
        Worker worker
) {

    /** 자동 분석 워커(백로그 소비) 설정. */
    public record Worker(
            boolean enabled,
            long pollIntervalMs,
            int batchSize,
            long stalePendingMinutes
    ) {
    }
}
