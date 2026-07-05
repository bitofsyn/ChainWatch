package com.chainwatch.backend.notification.service;

public interface NotificationDeduplicator {

    /** Returns true if a notification for this key was already sent within the TTL window. */
    boolean isDuplicate(String key);

    void markSent(String key);
}
