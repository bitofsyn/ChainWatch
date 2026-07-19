package com.chainwatch.backend.detection.config;

import java.math.BigDecimal;

/**
 * 룰 엔진이 판정에 사용하는 런타임 threshold 스냅샷(불변).
 * 기동 시 application.yml({@link DetectionProperties}) 값으로 시작하고,
 * 관리자 API로 변경되면 DB에 저장된 값이 우선한다.
 */
public record DetectionThresholds(
        BigDecimal largeTransferThresholdEth,
        BigDecimal exchangeFlowThresholdEth,
        int rapidTransferThresholdCount,
        long rapidTransferWindowMinutes,
        int fanOutThresholdRecipients,
        long fanOutWindowMinutes,
        long ruleCooldownMinutes
) {

    public static DetectionThresholds fromProperties(DetectionProperties properties) {
        return new DetectionThresholds(
                properties.largeTransferThresholdEth(),
                properties.exchangeFlowThresholdEth(),
                properties.rapidTransferThresholdCount(),
                properties.rapidTransferWindowMinutes(),
                properties.fanOutThresholdRecipients(),
                properties.fanOutWindowMinutes(),
                properties.ruleCooldownMinutes()
        );
    }
}
