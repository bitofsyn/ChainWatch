package com.chainwatch.backend.ops.api;

import com.chainwatch.backend.ops.service.OperationalSettingsService;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/operational-settings")
public class OperationalSettingsController {
    private final OperationalSettingsService service;
    public OperationalSettingsController(OperationalSettingsService service) { this.service = service; }

    @GetMapping public Response get() { return response(service.current()); }
    @PatchMapping public Response update(@RequestBody OperationalSettingsService.Update request, Authentication auth) {
        return response(service.update(request, auth.getName()));
    }

    private Response response(OperationalSettingsService.Snapshot value) {
        return new Response(value.collectorEnabled(), value.notificationEnabled(), value.minRiskScore(),
                hasText(value.slackWebhookUrl()), hasText(value.discordWebhookUrl()), value.updatedBy(), value.updatedAt());
    }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    public record Response(boolean collectorEnabled, boolean notificationEnabled, int minRiskScore,
            boolean slackConfigured, boolean discordConfigured, String updatedBy, Instant updatedAt) {}
}
