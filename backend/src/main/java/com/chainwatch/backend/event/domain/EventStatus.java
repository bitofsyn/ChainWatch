package com.chainwatch.backend.event.domain;

/**
 * 탐지 이벤트 운영 lifecycle 상태.
 * NEW → ACKNOWLEDGED → INVESTIGATING → RESOLVED 순서로 운영자가 전이시키고,
 * 오탐으로 판정된 이벤트는 FALSE_POSITIVE로 종결한다(falsePositiveReason 필수).
 */
public enum EventStatus {
    NEW,
    ACKNOWLEDGED,
    INVESTIGATING,
    RESOLVED,
    FALSE_POSITIVE
}
