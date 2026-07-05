package com.chainwatch.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";
    private static final String BEARER_FORMAT_JWT = "JWT";
    private static final String SCHEME_BEARER = "bearer";

    @Bean
    OpenAPI chainWatchOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ChainWatch API")
                        .description("AI 기반 온체인 이상거래 탐지 시스템 API")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme(SCHEME_BEARER)
                                .bearerFormat(BEARER_FORMAT_JWT)))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
