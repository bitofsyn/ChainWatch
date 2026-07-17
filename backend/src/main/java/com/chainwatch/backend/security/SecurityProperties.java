package com.chainwatch.backend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainwatch.security")
public record SecurityProperties(
        boolean jwtEnabled,
        String jwtSecret,
        long jwtExpirationMinutes,
        long refreshExpirationDays,
        String adminUsername,
        String adminPassword,
        String analystUsername,
        String analystPassword
) {
}
