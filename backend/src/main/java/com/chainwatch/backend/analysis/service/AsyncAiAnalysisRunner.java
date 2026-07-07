package com.chainwatch.backend.analysis.service;

import com.chainwatch.backend.analysis.config.AiAnalysisAsyncConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * AI 분석을 요청 스레드 밖에서 실행한다.
 * 분석 서버 지연/장애가 API 응답성에 전파되지 않고, 실패는 FAILED 리포트로 기록되어 조회 가능하다.
 */
@Component
public class AsyncAiAnalysisRunner {

    private static final Logger log = LoggerFactory.getLogger(AsyncAiAnalysisRunner.class);

    private final AiAnalysisService aiAnalysisService;

    public AsyncAiAnalysisRunner(AiAnalysisService aiAnalysisService) {
        this.aiAnalysisService = aiAnalysisService;
    }

    @Async(AiAnalysisAsyncConfig.AI_ANALYSIS_EXECUTOR)
    public void run(Long eventId) {
        try {
            aiAnalysisService.analyzeEvent(eventId);
            log.info("async ai analysis completed | eventId={}", eventId);
        } catch (Exception exception) {
            log.warn("async ai analysis failed | eventId={} error={}", eventId, exception.getMessage());
            try {
                aiAnalysisService.markFailed(eventId, exception.getMessage());
            } catch (Exception markException) {
                log.error("failed to record ai analysis failure | eventId={} error={}",
                        eventId, markException.getMessage());
            }
        }
    }
}
