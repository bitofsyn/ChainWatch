package com.chainwatch.backend.event.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 분석가 workflow 상태 전이 규칙 단위 테스트. */
class DetectionEventWorkflowTest {

    @Test
    void resolvedWithoutReasonIsRejected() {
        DetectionEvent event = newEvent();
        assertThatThrownBy(() -> event.applyStatusChange(EventStatus.RESOLVED, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resolutionReason");
        assertThat(event.getStatus()).isEqualTo(EventStatus.NEW);
    }

    @Test
    void falsePositiveWithoutReasonIsRejected() {
        DetectionEvent event = newEvent();
        assertThatThrownBy(() -> event.applyStatusChange(EventStatus.FALSE_POSITIVE, null, null, "  ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("falsePositiveReason");
    }

    @Test
    void resolvedWithReasonUpdatesWorkflowFields() {
        DetectionEvent event = newEvent();
        event.applyStatusChange(EventStatus.RESOLVED, "alice", "Confirmed legitimate transfer", null, "checked exchange wallet");

        assertThat(event.getStatus()).isEqualTo(EventStatus.RESOLVED);
        assertThat(event.getAssignee()).isEqualTo("alice");
        assertThat(event.getResolutionReason()).isEqualTo("Confirmed legitimate transfer");
        assertThat(event.getNotes()).isEqualTo("checked exchange wallet");
        assertThat(event.getStatusChangedAt()).isNotNull();
    }

    @Test
    void falsePositiveWithReasonIsAccepted() {
        DetectionEvent event = newEvent();
        event.applyStatusChange(EventStatus.FALSE_POSITIVE, null, null, "Known internal rebalancing wallet", null);

        assertThat(event.getStatus()).isEqualTo(EventStatus.FALSE_POSITIVE);
        assertThat(event.getFalsePositiveReason()).isEqualTo("Known internal rebalancing wallet");
    }

    @Test
    void nullOptionalFieldsPreserveExistingValues() {
        DetectionEvent event = newEvent();
        event.applyStatusChange(EventStatus.ACKNOWLEDGED, "alice", null, null, "first note");
        event.applyStatusChange(EventStatus.INVESTIGATING, null, null, null, null);

        assertThat(event.getStatus()).isEqualTo(EventStatus.INVESTIGATING);
        assertThat(event.getAssignee()).isEqualTo("alice");
        assertThat(event.getNotes()).isEqualTo("first note");
    }

    @Test
    void nullStatusIsRejected() {
        DetectionEvent event = newEvent();
        assertThatThrownBy(() -> event.applyStatusChange(null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static DetectionEvent newEvent() {
        return new DetectionEvent(
                EventType.LARGE_TRANSFER,
                RiskLevel.HIGH,
                90,
                "Large transfer detected",
                "0xabc",
                Instant.now(),
                null
        );
    }
}
