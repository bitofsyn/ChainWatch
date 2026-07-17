package com.chainwatch.backend.notification.service;

import com.chainwatch.backend.agentops.service.AgentFaultInjector;
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
    private final AgentFaultInjector faultInjector;

    public NotificationService(
            NotificationProperties properties,
            NotificationDeduplicator deduplicator,
            List<NotificationChannel> channels,
            NotificationHistoryRecorder historyRecorder,
            ChainWatchMetrics metrics,
            AgentFaultInjector faultInjector
    ) {
        this.properties = properties;
        this.deduplicator = deduplicator;
        this.channels = channels;
        this.historyRecorder = historyRecorder;
        this.metrics = metrics;
        this.faultInjector = faultInjector;
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

        // 장애 주입 활성 시 실제 발송 대신 채널별 실패 이력을 남긴다. 채널 미구성 환경에서도 확인
        // 가능하도록 구성 채널이 없으면 시뮬레이션 채널로 기록한다.
        if (faultInjector.isActive("notification")) {
            boolean anyConfigured = false;
            for (NotificationChannel channel : channels) {
                if (!channel.isConfigured()) {
                    continue;
                }
                anyConfigured = true;
                historyRecorder.record(message, channel.name(), false, "장애 주입 활성 — 발송 강제 실패");
                metrics.recordNotification(channel.name(), false);
            }
            if (!anyConfigured) {
                historyRecorder.record(message, "simulated", false, "장애 주입 활성 — 발송 강제 실패");
            }
            log.warn("notification forced to fail by fault injection | eventId={}", message.eventId());
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
