package com.chainwatch.backend.analysis.api;

import com.chainwatch.backend.analysis.service.AiAnalysisService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/{eventId}/analysis")
public class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;

    public AiAnalysisController(AiAnalysisService aiAnalysisService) {
        this.aiAnalysisService = aiAnalysisService;
    }

    @PostMapping
    public AiAnalysisReportResponse analyze(@PathVariable Long eventId) {
        return AiAnalysisReportResponse.from(aiAnalysisService.analyzeEvent(eventId));
    }
}
