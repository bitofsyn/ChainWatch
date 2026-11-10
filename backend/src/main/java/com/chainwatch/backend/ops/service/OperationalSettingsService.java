package com.chainwatch.backend.ops.service;

import com.chainwatch.backend.audit.service.AuditLogService;
import com.chainwatch.backend.collector.config.CollectorProperties;
import com.chainwatch.backend.notification.config.NotificationProperties;
import com.chainwatch.backend.ops.domain.OperationalSettings;
import com.chainwatch.backend.ops.repository.OperationalSettingsRepository;
import java.net.URI;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationalSettingsService {
    private final OperationalSettingsRepository repository;
    private final CollectorProperties collectorDefaults;
    private final NotificationProperties notificationDefaults;
    private final AuditLogService audit;

    public OperationalSettingsService(OperationalSettingsRepository repository, CollectorProperties collectorDefaults,
            NotificationProperties notificationDefaults, AuditLogService audit) {
        this.repository = repository;
        this.collectorDefaults = collectorDefaults;
        this.notificationDefaults = notificationDefaults;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Snapshot current() {
        return repository.findById(OperationalSettings.SINGLETON_ID).map(this::snapshot)
                .orElseGet(() -> new Snapshot(collectorDefaults.enabled(), notificationDefaults.enabled(),
                        notificationDefaults.minRiskScore(), notificationDefaults.slackWebhookUrl(),
                        notificationDefaults.discordWebhookUrl(), null, null));
    }

    @Transactional
    public Snapshot update(Update update, String actor) {
        Snapshot before = current();
        boolean collectorEnabled = update.collectorEnabled() == null ? before.collectorEnabled() : update.collectorEnabled();
        boolean notificationEnabled = update.notificationEnabled() == null ? before.notificationEnabled() : update.notificationEnabled();
        int minRiskScore = update.minRiskScore() == null ? before.minRiskScore() : update.minRiskScore();
        if (minRiskScore < 0 || minRiskScore > 100) throw new IllegalArgumentException("알림 위험 점수는 0~100이어야 합니다.");
        String slack = update.slackWebhookUrl() == null ? before.slackWebhookUrl() : validateWebhook(update.slackWebhookUrl(), "Slack");
        String discord = update.discordWebhookUrl() == null ? before.discordWebhookUrl() : validateWebhook(update.discordWebhookUrl(), "Discord");
        if (notificationEnabled && isBlank(slack) && isBlank(discord)) {
            throw new IllegalArgumentException("알림을 활성화하려면 Slack 또는 Discord 웹훅이 필요합니다.");
        }
        OperationalSettings entity = repository.findById(OperationalSettings.SINGLETON_ID)
                .orElseGet(() -> new OperationalSettings(collectorEnabled, notificationEnabled, minRiskScore, slack, discord, actor, Instant.now()));
        entity.apply(collectorEnabled, notificationEnabled, minRiskScore, slack, discord, actor, Instant.now());
        repository.save(entity);
        audit.record("OPERATIONAL_SETTINGS_UPDATE", "OPERATIONAL_SETTINGS", "1",
                "collector=" + collectorEnabled + ", notification=" + notificationEnabled + ", minRiskScore=" + minRiskScore);
        return snapshot(entity);
    }

    private String validateWebhook(String value, String channel) {
        if (isBlank(value)) return null;
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException();
            return uri.toString();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(channel + " 웹훅은 유효한 HTTPS URL이어야 합니다.");
        }
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }
    private Snapshot snapshot(OperationalSettings value) { return new Snapshot(value.isCollectorEnabled(), value.isNotificationEnabled(), value.getMinRiskScore(), value.getSlackWebhookUrl(), value.getDiscordWebhookUrl(), value.getUpdatedBy(), value.getUpdatedAt()); }

    public record Snapshot(boolean collectorEnabled, boolean notificationEnabled, int minRiskScore,
            String slackWebhookUrl, String discordWebhookUrl, String updatedBy, Instant updatedAt) {}
    public record Update(Boolean collectorEnabled, Boolean notificationEnabled, Integer minRiskScore,
            String slackWebhookUrl, String discordWebhookUrl) {}
}
