package com.chainwatch.backend.event.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 저위험 backlog 자동 만료 정책.
 * 룰 과발화로 쌓인 MEDIUM/LOW 미처리(NEW) 이벤트를 보존 기간 경과 시 자동 종결한다.
 * CRITICAL/HIGH는 절대 대상이 아니다 — 대응 backlog KPI의 대상이며 사람이 처리해야 한다.
 */
@ConfigurationProperties(prefix = "chainwatch.events.expiry")
public record EventExpiryProperties(
        boolean enabled,
        /** NEW 상태로 이 일수를 초과 대기한 MEDIUM/LOW 이벤트를 자동 종결한다 */
        long maxAgeDays
) {

    public EventExpiryProperties {
        if (maxAgeDays <= 0) {
            maxAgeDays = 3;
        }
    }
}
