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
                    "AI 분석이 비활성화되어 있어 리포트를 생성할 수 없습니다. "
                            + "관리자가 AI 분석 서버를 설정하면 사용할 수 있습니다."
            );
        };
    }
}
