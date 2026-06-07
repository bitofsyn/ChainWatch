package com.chainwatch.backend.analysis.config;

import com.chainwatch.backend.analysis.client.AiAnalysisClient;
import com.chainwatch.backend.analysis.exception.AiAnalysisException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiAnalysisClientFallbackConfig {

    @Bean
    @ConditionalOnMissingBean(AiAnalysisClient.class)
    public AiAnalysisClient aiAnalysisClient() {
        return request -> {
            throw new AiAnalysisException(
                    "AI analysis server is not configured. Set chainwatch.ai.enabled=true and chainwatch.ai.base-url."
            );
        };
    }
}
