package com.chainwatch.backend.event.api;

import java.time.Instant;
import java.util.List;

public record EventTrendResponse(
        int hours,
        List<TrendPoint> points
) {
    public record TrendPoint(Instant bucketStart, long count) {
    }
}
