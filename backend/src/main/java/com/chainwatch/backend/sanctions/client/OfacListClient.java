package com.chainwatch.backend.sanctions.client;

import com.chainwatch.backend.sanctions.config.SanctionsProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** 제재 주소 소스 리스트(주소 1줄 1개 텍스트)를 내려받는다. */
@Component
public class OfacListClient {

    private final RestClient restClient;
    private final SanctionsProperties properties;

    public OfacListClient(RestClient.Builder restClientBuilder, SanctionsProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    public String fetchRaw() {
        return restClient.get()
                .uri(properties.sourceUrl())
                .retrieve()
                .body(String.class);
    }
}
