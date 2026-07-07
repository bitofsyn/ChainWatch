package com.chainwatch.backend.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainwatch.notification")
public record NotificationProperties(
        boolean enabled,
        int minRiskScore,
        long dedupTtlMinutes,
        String dedupStore,
        String slackWebhookUrl,
        String discordWebhookUrl
) {
}
