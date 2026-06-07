package com.chainwatch.backend.analysis.client;

import com.chainwatch.backend.analysis.config.AiAnalysisProperties;
import com.chainwatch.backend.analysis.exception.AiAnalysisException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@ConditionalOnProperty(prefix = "chainwatch.ai", name = "enabled", havingValue = "true")
public class FastApiAiAnalysisClient implements AiAnalysisClient {

    private final WebClient webClient;
    private final AiAnalysisProperties properties;

    public FastApiAiAnalysisClient(WebClient.Builder webClientBuilder, AiAnalysisProperties properties) {
        this.webClient = webClientBuilder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
    }

    @Override
    public AiAnalysisResult analyze(AiAnalysisRequest request) {
        FastApiAiAnalysisResponse response = webClient.post()
                .uri(properties.analyzePath())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(FastApiAiAnalysisResponse.class)
                .block();

        if (response == null || response.report() == null || response.report().isBlank()) {
            throw new AiAnalysisException("AI analysis server returned an empty report");
        }

        return new AiAnalysisResult(response.report(), response.rawResponse());
    }

    private record FastApiAiAnalysisResponse(
            String report,
            String rawResponse
    ) {
    }
}
