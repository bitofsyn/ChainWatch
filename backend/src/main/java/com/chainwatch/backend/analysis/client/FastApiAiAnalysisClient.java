package com.chainwatch.backend.analysis.client;

import com.chainwatch.backend.analysis.config.AiAnalysisProperties;
import com.chainwatch.backend.analysis.exception.AiAnalysisException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@ConditionalOnProperty(prefix = "chainwatch.ai", name = "enabled", havingValue = "true")
public class FastApiAiAnalysisClient implements AiAnalysisClient {

    private static final Logger log = LoggerFactory.getLogger(FastApiAiAnalysisClient.class);

    /** AI 서버의 프로바이더 폴백(최악 ~90s: claude 45s → gemini 45s)을 수용한다. 호출은 @Async 경로. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);

    private final WebClient webClient;
    private final AiAnalysisProperties properties;
    private final ObjectMapper objectMapper;

    public FastApiAiAnalysisClient(
            WebClient.Builder webClientBuilder,
            AiAnalysisProperties properties,
            ObjectMapper objectMapper
    ) {
        this.webClient = webClientBuilder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiAnalysisResult analyze(AiAnalysisRequest request) {
        FastApiAiAnalysisResponse response;
        try {
            response = webClient.post()
                    .uri(properties.analyzePath())
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(FastApiAiAnalysisResponse.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();
        } catch (AiAnalysisException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiAnalysisException("AI analysis request failed or timed out", exception);
        }

        if (response == null || response.report() == null || response.report().isBlank()) {
            throw new AiAnalysisException("AI analysis server returned an empty report");
        }

        return new AiAnalysisResult(
                response.report(),
                response.rawResponse(),
                toStructuredJson(response),
                response.provider(),
                response.model()
        );
    }

    /** 구조화 필드를 JSON 문자열로 직렬화한다. 비구조화 응답이거나 직렬화 실패 시 null. */
    private String toStructuredJson(FastApiAiAnalysisResponse response) {
        if (!Boolean.TRUE.equals(response.structured())) {
            return null;
        }
        AiStructuredAnalysis structured = new AiStructuredAnalysis(
                response.riskSummary(),
                response.evidence(),
                response.possibleScenarios(),
                response.recommendedActions(),
                response.confidence(),
                response.falsePositiveFactors(),
                response.escalationLevel()
        );
        try {
            return objectMapper.writeValueAsString(structured);
        } catch (JsonProcessingException exception) {
            log.warn("Failed to serialize structured AI analysis; keeping text-only report", exception);
            return null;
        }
    }

    private record FastApiAiAnalysisResponse(
            String report,
            String rawResponse,
            String provider,
            String model,
            Boolean structured,
            String riskSummary,
            List<AiStructuredAnalysis.EvidenceItem> evidence,
            List<String> possibleScenarios,
            List<String> recommendedActions,
            String confidence,
            List<String> falsePositiveFactors,
            String escalationLevel
    ) {
    }
}
