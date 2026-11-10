package com.chainwatch.backend.notification.channel;

import com.chainwatch.backend.notification.domain.NotificationMessage;
import com.chainwatch.backend.ops.service.OperationalSettingsService;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SlackNotificationChannel extends WebhookNotificationChannel {

    private static final String CHANNEL_NAME = "slack";
    private static final String PAYLOAD_KEY_TEXT = "text";

    public SlackNotificationChannel(RestClient.Builder restClientBuilder, OperationalSettingsService settings) {
        super(restClientBuilder.build(), () -> settings.current().slackWebhookUrl());
    }

    @Override
    public String name() {
        return CHANNEL_NAME;
    }

    @Override
    protected Map<String, Object> buildPayload(NotificationMessage message) {
        return Map.of(PAYLOAD_KEY_TEXT, NotificationTextFormatter.format(message));
    }
}
