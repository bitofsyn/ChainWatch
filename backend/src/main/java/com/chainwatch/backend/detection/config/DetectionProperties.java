package com.chainwatch.backend.detection.config;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainwatch.detection")
public record DetectionProperties(
        DetectionTransport mode,
        BigDecimal largeTransferThresholdEth,
        BigDecimal exchangeFlowThresholdEth,
        int rapidTransferThresholdCount,
        long rapidTransferWindowMinutes,
        int fanOutThresholdRecipients,
        long fanOutWindowMinutes,
        /**
         * 지갑 단위 재발화 억제 창(분). 같은 지갑·같은 패턴 룰(RAPID_TRANSFER/FAN_OUT)의 이벤트가
         * 이 시간 안에 이미 있으면 룰 평가를 건너뛴다(트랜잭션마다 중복 발화 방지 + 집계 쿼리 절감).
         * 0 이하면 비활성.
         */
        long ruleCooldownMinutes,
        List<String> watchlistAddresses,
        List<String> exchangeAddresses
) {

    public DetectionProperties {
        if (mode == null) {
            mode = DetectionTransport.SYNC;
        }
    }

    public boolean isKafkaMode() {
        return mode == DetectionTransport.KAFKA;
    }

    /**
     * 탐지 트리거 방식.
     * SYNC: Collector가 블록 처리 트랜잭션 안에서 탐지를 직접 호출 (Kafka 없는 로컬 환경용).
     * KAFKA: raw-transactions 토픽을 구독하는 Consumer가 탐지 수행 (Detection Server 분리 대비).
     */
    public enum DetectionTransport {
        SYNC,
        KAFKA
    }
}
