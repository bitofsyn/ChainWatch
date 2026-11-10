package com.chainwatch.backend.notification.channel;

import com.chainwatch.backend.notification.domain.NotificationMessage;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import java.util.function.Supplier;

/** Common webhook POST logic for JSON-payload channels (Slack, Discord). */
public abstract class WebhookNotificationChannel implements NotificationChannel {

    private final RestClient restClient;
    private final Supplier<String> webhookUrl;

    protected WebhookNotificationChannel(RestClient restClient, Supplier<String> webhookUrl) {
        this.restClient = restClient;
        this.webhookUrl = webhookUrl;
    }

    protected abstract Map<String, Object> buildPayload(NotificationMessage message);

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(webhookUrl.get());
    }

    @Override
    public void send(NotificationMessage message) {
        restClient.post()
                .uri(webhookUrl.get())
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildPayload(message))
                .retrieve()
                .toBodilessEntity();
    }
}
