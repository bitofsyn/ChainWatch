package com.chainwatch.backend.event.domain;

/**
 * 탐지 이벤트 운영 lifecycle 상태.
 * NEW → ACKNOWLEDGED → INVESTIGATING → RESOLVED 순서로 운영자가 전이시킨다.
 */
public enum EventStatus {
    NEW,
    ACKNOWLEDGED,
    INVESTIGATING,
    RESOLVED
}
