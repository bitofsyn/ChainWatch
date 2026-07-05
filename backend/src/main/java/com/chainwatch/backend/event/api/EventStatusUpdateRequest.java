package com.chainwatch.backend.event.api;

import com.chainwatch.backend.event.domain.EventStatus;
import jakarta.validation.constraints.NotNull;

public record EventStatusUpdateRequest(
        @NotNull EventStatus status
) {
}
