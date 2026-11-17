package com.chainwatch.backend.ops.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 선택 인프라 사용 여부. Kafka·Redis 없이 단독 실행하는 구성(무료 호스팅 등)에서는 false로 두어
 * 해당 구성 요소를 "장애"가 아닌 "미사용"으로 보고하고, 연결 시도로 응답이 지연되지 않게 한다.
 */
@ConfigurationProperties(prefix = "chainwatch.infra")
public record InfraProperties(
        boolean kafkaEnabled,
        boolean redisEnabled
) {
}
