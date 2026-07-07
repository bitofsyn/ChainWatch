package com.chainwatch.backend.analysis.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * AI 분석 전용 비동기 executor.
 * 분석 지연이 API 요청 스레드를 점유하지 않도록 격리하고,
 * 큐 포화 시 CallerRunsPolicy로 백프레셔를 걸어 무제한 적체를 막는다.
 */
@Configuration
@EnableAsync
public class AiAnalysisAsyncConfig {

    public static final String AI_ANALYSIS_EXECUTOR = "aiAnalysisExecutor";

    @Bean(name = AI_ANALYSIS_EXECUTOR)
    ThreadPoolTaskExecutor aiAnalysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-analysis-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
