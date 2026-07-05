package com.chainwatch.backend.notification.channel;

import com.chainwatch.backend.notification.config.NotificationProperties;
import com.chainwatch.backend.notification.domain.NotificationMessage;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DiscordNotificationChannel extends WebhookNotificationChannel {

    private static final String CHANNEL_NAME = "discord";
    private static final String PAYLOAD_KEY_CONTENT = "content";

    public DiscordNotificationChannel(RestClient.Builder restClientBuilder, NotificationProperties properties) {
        super(restClientBuilder.build(), properties.discordWebhookUrl());
    }

    @Override
    public String name() {
        return CHANNEL_NAME;
    }

    @Override
    protected Map<String, Object> buildPayload(NotificationMessage message) {
        return Map.of(PAYLOAD_KEY_CONTENT, NotificationTextFormatter.format(message));
    }
}
