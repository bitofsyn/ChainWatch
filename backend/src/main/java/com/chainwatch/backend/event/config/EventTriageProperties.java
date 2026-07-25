package com.chainwatch.backend.event.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 저위험 중복 이벤트 자동 트리아지 정책.
 * 같은 지갑·같은 유형의 MEDIUM/LOW 미처리(NEW) 이벤트가 중복 누적되면 최신 1건만 남기고
 * 자동 종결한다 — 룰 과발화가 재발해도 대응 큐가 지갑·유형당 1건으로 수렴한다.
 * CRITICAL/HIGH는 절대 대상이 아니다(대응 backlog KPI, 사람이 처리).
 */
@ConfigurationProperties(prefix = "chainwatch.events.triage")
public record EventTriageProperties(
        boolean enabled
) {
}
