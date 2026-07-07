package com.chainwatch.backend.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * ChainWatch 도메인 지표. /actuator/prometheus로 노출되어
 * 탐지량, 알림 성공/실패, AI 분석 결과를 Prometheus에서 추적할 수 있다.
 */
@Component
public class ChainWatchMetrics {

    private final MeterRegistry meterRegistry;

    public ChainWatchMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordDetectionEvent(String eventType, String riskLevel) {
        Counter.builder("chainwatch.detection.events")
                .description("Detection events persisted")
                .tag("event_type", safe(eventType))
                .tag("risk_level", safe(riskLevel))
                .register(meterRegistry)
                .increment();
    }

    public void recordNotification(String channel, boolean success) {
        Counter.builder("chainwatch.notifications.sent")
                .description("Notification delivery attempts")
                .tag("channel", safe(channel))
                .tag("result", success ? "success" : "failure")
                .register(meterRegistry)
                .increment();
    }

    public void recordAiAnalysis(String status) {
        Counter.builder("chainwatch.ai.analysis")
                .description("AI analysis reports by resulting status")
                .tag("status", safe(status))
                .register(meterRegistry)
                .increment();
    }

    private String safe(String value) {
        return value != null ? value : "unknown";
    }
}
