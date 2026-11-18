package com.chainwatch.backend.analysis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainwatch.ai")
public record AiAnalysisProperties(
        boolean enabled,
        String provider,
        String model,
        String baseUrl,
        String analyzePath,
        Worker worker,
        // AI 서버가 공개 주소일 때 함께 보내는 공유 토큰(X-ChainWatch-AI-Token). 비어 있으면 보내지 않는다.
        String apiToken
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
