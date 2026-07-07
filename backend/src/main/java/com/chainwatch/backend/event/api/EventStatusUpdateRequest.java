package com.chainwatch.backend.event.api;

import com.chainwatch.backend.event.domain.EventStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 상태 변경 + 분석가 workflow 갱신 요청.
 * RESOLVED는 resolutionReason, FALSE_POSITIVE는 falsePositiveReason이 필수다(도메인 규칙으로 검증).
 * assignee/notes는 전달된 경우에만 갱신된다.
 */
public record EventStatusUpdateRequest(
        @NotNull EventStatus status,
        @Size(max = 100) String assignee,
        @Size(max = 500) String resolutionReason,
        @Size(max = 500) String falsePositiveReason,
        @Size(max = 2000) String notes
) {
}
