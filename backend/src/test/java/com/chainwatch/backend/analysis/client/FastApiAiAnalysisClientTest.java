package com.chainwatch.backend.analysis.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.chainwatch.backend.analysis.config.AiAnalysisProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class FastApiAiAnalysisClientTest {

    private static final String RESPONSE_BODY = """
            {"report":"리포트","rawResponse":"{}","provider":"gemini","model":"gemini-flash","structured":false}
            """;

    @Test
    void sendsSharedTokenHeaderWhenConfigured() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();

        analyzeWith("shared-token", captured);

        assertThat(captured.get().headers().getFirst(FastApiAiAnalysisClient.TOKEN_HEADER))
                .isEqualTo("shared-token");
    }

    @Test
    void omitsTokenHeaderWhenNotConfigured() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();

        analyzeWith("", captured);

        assertThat(captured.get().headers().containsKey(FastApiAiAnalysisClient.TOKEN_HEADER)).isFalse();
    }

    private void analyzeWith(String token, AtomicReference<ClientRequest> captured) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            captured.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(RESPONSE_BODY)
                    .build());
        });
        AiAnalysisProperties properties = new AiAnalysisProperties(
                true, "", "", "http://ai.invalid", "/api/v1/analyze",
                new AiAnalysisProperties.Worker(false, 30_000, 3, 10), token);

        AiAnalysisResult result = new FastApiAiAnalysisClient(builder, properties, new ObjectMapper())
                .analyze(new AiAnalysisRequest(1L, null, null, "LARGE_TRANSFER", "HIGH", 85,
                        "0xabc", "0xdef", "대규모 이체"));

        assertThat(result.report()).isEqualTo("리포트");
    }
}
