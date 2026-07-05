package com.chainwatch.backend.ops.api;

import java.time.Instant;
import java.util.List;

public record PipelineStatusResponse(
        Instant checkedAt,
        List<ComponentStatus> components
) {
    /**
     * status: UP(정상) / DOWN(장애) / DISABLED(설정상 비활성).
     */
    public record ComponentStatus(
            String name,
            String status,
            String detail
    ) {
        public static ComponentStatus up(String name, String detail) {
            return new ComponentStatus(name, "UP", detail);
        }

        public static ComponentStatus down(String name, String detail) {
            return new ComponentStatus(name, "DOWN", detail);
        }

        public static ComponentStatus disabled(String name, String detail) {
            return new ComponentStatus(name, "DISABLED", detail);
        }
    }
}
