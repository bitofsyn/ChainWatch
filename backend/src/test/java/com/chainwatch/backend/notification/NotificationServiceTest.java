package com.chainwatch.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.chainwatch.backend.notification.channel.NotificationChannel;
import com.chainwatch.backend.notification.config.NotificationProperties;
import com.chainwatch.backend.notification.domain.NotificationMessage;
import com.chainwatch.backend.notification.service.NotificationDeduplicator;
import com.chainwatch.backend.notification.service.NotificationService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {

    private static class RecordingChannel implements NotificationChannel {
        private final String name;
        private final boolean configured;
        private final boolean failing;
        final List<NotificationMessage> sent = new ArrayList<>();

        RecordingChannel(String name, boolean configured, boolean failing) {
            this.name = name;
            this.configured = configured;
            this.failing = failing;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public void send(NotificationMessage message) {
            if (failing) {
                throw new IllegalStateException("simulated channel failure");
            }
            sent.add(message);
        }
    }

    private static class RecordingDeduplicator implements NotificationDeduplicator {
        final Set<String> sentKeys = new HashSet<>();

        @Override
        public boolean isDuplicate(String key) {
            return sentKeys.contains(key);
        }

        @Override
        public void markSent(String key) {
            sentKeys.add(key);
        }
    }

    private static NotificationMessage message(long eventId, int riskScore) {
        return new NotificationMessage(
                eventId, "LARGE_TRANSFER", "HIGH", riskScore,
                "고액 이체 탐지", "0xabc", "0xdef", Instant.now());
    }

    private static NotificationProperties properties(boolean enabled, int minRiskScore) {
        return new NotificationProperties(enabled, minRiskScore, 30, "https://slack", "https://discord");
    }

    @Test
    void sendsToAllConfiguredChannels() {
        RecordingChannel slack = new RecordingChannel("slack", true, false);
        RecordingChannel discord = new RecordingChannel("discord", true, false);
        NotificationService service =
                new NotificationService(properties(true, 70), new RecordingDeduplicator(), List.of(slack, discord));

        service.notify(message(1, 90));

        assertThat(slack.sent).hasSize(1);
        assertThat(discord.sent).hasSize(1);
    }

    @Test
    void skipsWhenDisabled() {
        RecordingChannel slack = new RecordingChannel("slack", true, false);
        NotificationService service =
                new NotificationService(properties(false, 70), new RecordingDeduplicator(), List.of(slack));

        service.notify(message(1, 90));

        assertThat(slack.sent).isEmpty();
    }

    @Test
    void skipsBelowRiskThreshold() {
        RecordingChannel slack = new RecordingChannel("slack", true, false);
        NotificationService service =
                new NotificationService(properties(true, 70), new RecordingDeduplicator(), List.of(slack));

        service.notify(message(1, 69));

        assertThat(slack.sent).isEmpty();
    }

    @Test
    void suppressesDuplicateNotifications() {
        RecordingChannel slack = new RecordingChannel("slack", true, false);
        NotificationService service =
                new NotificationService(properties(true, 70), new RecordingDeduplicator(), List.of(slack));

        service.notify(message(1, 90));
        service.notify(message(1, 90));

        assertThat(slack.sent).hasSize(1);
    }

    @Test
    void skipsUnconfiguredChannels() {
        RecordingChannel unconfigured = new RecordingChannel("slack", false, false);
        NotificationService service =
                new NotificationService(properties(true, 70), new RecordingDeduplicator(), List.of(unconfigured));

        service.notify(message(1, 90));

        assertThat(unconfigured.sent).isEmpty();
    }

    @Test
    void channelFailureDoesNotBlockOtherChannels() {
        RecordingChannel failing = new RecordingChannel("slack", true, true);
        RecordingChannel healthy = new RecordingChannel("discord", true, false);
        RecordingDeduplicator deduplicator = new RecordingDeduplicator();
        NotificationService service =
                new NotificationService(properties(true, 70), deduplicator, List.of(failing, healthy));

        service.notify(message(1, 90));

        assertThat(healthy.sent).hasSize(1);
        assertThat(deduplicator.sentKeys).contains("event:1");
    }

    @Test
    void doesNotMarkSentWhenEveryChannelFails() {
        RecordingChannel failing = new RecordingChannel("slack", true, true);
        RecordingDeduplicator deduplicator = new RecordingDeduplicator();
        NotificationService service =
                new NotificationService(properties(true, 70), deduplicator, List.of(failing));

        service.notify(message(1, 90));

        assertThat(deduplicator.sentKeys).isEmpty();
    }
}
