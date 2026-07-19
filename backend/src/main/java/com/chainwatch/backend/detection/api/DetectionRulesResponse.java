package com.chainwatch.backend.detection.api;

import com.chainwatch.backend.detection.config.DetectionThresholds;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DetectionRulesResponse(
        String mode,
        List<Rule> rules,
        Thresholds thresholds,
        List<String> watchlistAddresses,
        List<String> exchangeAddresses,
        /** 마지막 관리자 변경자. null이면 application.yml 기본값 적용 중 */
        String thresholdsUpdatedBy,
        Instant thresholdsUpdatedAt
) {
    public record Rule(
            String eventType,
            String name,
            String description,
            String threshold,
            String baseRiskLevel,
            int baseRiskScore,
            boolean active
    ) {
    }

    /** 관리자 수정 폼용 원본 threshold 값 */
    public record Thresholds(
            BigDecimal largeTransferThresholdEth,
            BigDecimal exchangeFlowThresholdEth,
            int rapidTransferThresholdCount,
            long rapidTransferWindowMinutes,
            int fanOutThresholdRecipients,
            long fanOutWindowMinutes,
            long ruleCooldownMinutes
    ) {
        public static Thresholds from(DetectionThresholds thresholds) {
            return new Thresholds(
                    thresholds.largeTransferThresholdEth(),
                    thresholds.exchangeFlowThresholdEth(),
                    thresholds.rapidTransferThresholdCount(),
                    thresholds.rapidTransferWindowMinutes(),
                    thresholds.fanOutThresholdRecipients(),
                    thresholds.fanOutWindowMinutes(),
                    thresholds.ruleCooldownMinutes()
            );
        }
    }
}
