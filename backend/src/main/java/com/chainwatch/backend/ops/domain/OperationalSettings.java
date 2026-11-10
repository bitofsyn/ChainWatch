package com.chainwatch.backend.ops.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "operational_settings")
public class OperationalSettings {
    public static final long SINGLETON_ID = 1L;

    @Id private Long id;
    @Column(nullable = false) private boolean collectorEnabled;
    @Column(nullable = false) private boolean notificationEnabled;
    @Column(nullable = false) private int minRiskScore;
    @Column(length = 2000) private String slackWebhookUrl;
    @Column(length = 2000) private String discordWebhookUrl;
    @Column(nullable = false, length = 100) private String updatedBy;
    @Column(nullable = false) private Instant updatedAt;

    protected OperationalSettings() {}

    public OperationalSettings(boolean collectorEnabled, boolean notificationEnabled, int minRiskScore,
            String slackWebhookUrl, String discordWebhookUrl, String updatedBy, Instant updatedAt) {
        this.id = SINGLETON_ID;
        apply(collectorEnabled, notificationEnabled, minRiskScore, slackWebhookUrl, discordWebhookUrl, updatedBy, updatedAt);
    }

    public void apply(boolean collectorEnabled, boolean notificationEnabled, int minRiskScore,
            String slackWebhookUrl, String discordWebhookUrl, String updatedBy, Instant updatedAt) {
        this.collectorEnabled = collectorEnabled;
        this.notificationEnabled = notificationEnabled;
        this.minRiskScore = minRiskScore;
        this.slackWebhookUrl = blankToNull(slackWebhookUrl);
        this.discordWebhookUrl = blankToNull(discordWebhookUrl);
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    public boolean isCollectorEnabled() { return collectorEnabled; }
    public boolean isNotificationEnabled() { return notificationEnabled; }
    public int getMinRiskScore() { return minRiskScore; }
    public String getSlackWebhookUrl() { return slackWebhookUrl; }
    public String getDiscordWebhookUrl() { return discordWebhookUrl; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
