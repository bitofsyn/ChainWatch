package com.chainwatch.backend.notification.channel;

import com.chainwatch.backend.notification.domain.NotificationMessage;

public interface NotificationChannel {

    String name();

    /** A channel is dispatched only when its webhook URL is configured. */
    boolean isConfigured();

    void send(NotificationMessage message);
}
