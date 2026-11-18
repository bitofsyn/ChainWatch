package com.chainwatch.backend.notification.service;

import com.chainwatch.backend.common.metrics.ChainWatchMetrics;
import com.chainwatch.backend.notification.channel.NotificationChannel;
import com.chainwatch.backend.notification.domain.NotificationMessage;
import com.chainwatch.backend.ops.service.OperationalSettingsService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationDeduplicator deduplicator;
    private final List<NotificationChannel> channels;
    private final NotificationHistoryRecorder historyRecorder;
    private final ChainWatchMetrics metrics;
    private final OperationalSettingsService operationalSettings;

    public NotificationService(
            NotificationDeduplicator deduplicator,
            List<NotificationChannel> channels,
            NotificationHistoryRecorder historyRecorder,
            ChainWatchMetrics metrics,
            OperationalSettingsService operationalSettings
    ) {
        this.deduplicator = deduplicator;
        this.channels = channels;
        this.historyRecorder = historyRecorder;
        this.metrics = metrics;
        this.operationalSettings = operationalSettings;
    }

    public void notify(NotificationMessage message) {
        var current = operationalSettings.current();
        if (!current.notificationEnabled()) {
            return;
        }
        if (message.riskScore() < current.minRiskScore()) {
            log.debug("notification skipped | eventId={} riskScore={} < threshold={}",
                    message.eventId(), message.riskScore(), current.minRiskScore());
            return;
        }
        String dedupKey = message.dedupKey();
        if (deduplicator.isDuplicate(dedupKey)) {
            log.debug("notification suppressed as duplicate | key={}", dedupKey);
            return;
        }

        boolean anySent = false;
        for (NotificationChannel channel : channels) {
            if (!channel.isConfigured()) {
                continue;
            }
            long startedNanos = System.nanoTime();
            try {
                channel.send(message);
                long durationMs = Math.max(1, (System.nanoTime() - startedNanos) / 1_000_000);
                anySent = true;
                historyRecorder.record(message, channel.name(), true, null, durationMs);
                metrics.recordNotification(channel.name(), true);
                log.info("notification sent | channel={} eventId={} riskScore={} durationMs={}",
                        channel.name(), message.eventId(), message.riskScore(), durationMs);
            } catch (Exception exception) {
                long durationMs = Math.max(1, (System.nanoTime() - startedNanos) / 1_000_000);
                historyRecorder.record(message, channel.name(), false, exception.getMessage(), durationMs);
                metrics.recordNotification(channel.name(), false);
                log.warn("notification failed | channel={} eventId={} error={}",
                        channel.name(), message.eventId(), exception.getMessage());
            }
        }

        if (anySent) {
            deduplicator.markSent(dedupKey);
        }
    }
}
