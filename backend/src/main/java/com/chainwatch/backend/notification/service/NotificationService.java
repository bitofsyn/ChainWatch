package com.chainwatch.backend.notification.service;

import com.chainwatch.backend.common.metrics.ChainWatchMetrics;
import com.chainwatch.backend.notification.channel.NotificationChannel;
import com.chainwatch.backend.notification.config.NotificationProperties;
import com.chainwatch.backend.notification.domain.NotificationMessage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationProperties properties;
    private final NotificationDeduplicator deduplicator;
    private final List<NotificationChannel> channels;
    private final NotificationHistoryRecorder historyRecorder;
    private final ChainWatchMetrics metrics;

    public NotificationService(
            NotificationProperties properties,
            NotificationDeduplicator deduplicator,
            List<NotificationChannel> channels,
            NotificationHistoryRecorder historyRecorder,
            ChainWatchMetrics metrics
    ) {
        this.properties = properties;
        this.deduplicator = deduplicator;
        this.channels = channels;
        this.historyRecorder = historyRecorder;
        this.metrics = metrics;
    }

    public void notify(NotificationMessage message) {
        if (!properties.enabled()) {
            return;
        }
        if (message.riskScore() < properties.minRiskScore()) {
            log.debug("notification skipped | eventId={} riskScore={} < threshold={}",
                    message.eventId(), message.riskScore(), properties.minRiskScore());
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
            try {
                channel.send(message);
                anySent = true;
                historyRecorder.record(message, channel.name(), true, null);
                metrics.recordNotification(channel.name(), true);
                log.info("notification sent | channel={} eventId={} riskScore={}",
                        channel.name(), message.eventId(), message.riskScore());
            } catch (Exception exception) {
                historyRecorder.record(message, channel.name(), false, exception.getMessage());
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
