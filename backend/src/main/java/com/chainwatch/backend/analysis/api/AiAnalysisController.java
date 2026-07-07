package com.chainwatch.backend.analysis.api;

import com.chainwatch.backend.analysis.service.AiAnalysisService;
import com.chainwatch.backend.analysis.service.AsyncAiAnalysisRunner;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/{eventId}/analysis")
public class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;
    private final AsyncAiAnalysisRunner asyncAiAnalysisRunner;

    public AiAnalysisController(AiAnalysisService aiAnalysisService, AsyncAiAnalysisRunner asyncAiAnalysisRunner) {
        this.aiAnalysisService = aiAnalysisService;
        this.asyncAiAnalysisRunner = asyncAiAnalysisRunner;
    }

    @PostMapping
    public AiAnalysisReportResponse analyze(@PathVariable Long eventId) {
        return AiAnalysisReportResponse.from(aiAnalysisService.analyzeEvent(eventId));
    }

    /**
     * 비동기 분석: PENDING 리포트를 즉시 반환하고 분석은 전용 executor에서 실행한다.
     * 결과는 이벤트 상세 조회(analysis 필드)로 확인한다.
     */
    @PostMapping("/async")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AiAnalysisReportResponse analyzeAsync(@PathVariable Long eventId) {
        AiAnalysisReportResponse pending = AiAnalysisReportResponse.from(aiAnalysisService.markPending(eventId));
        asyncAiAnalysisRunner.run(eventId);
        return pending;
    }
}
