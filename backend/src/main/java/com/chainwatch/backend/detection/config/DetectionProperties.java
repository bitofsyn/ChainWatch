package com.chainwatch.backend.detection.config;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainwatch.detection")
public record DetectionProperties(
        BigDecimal largeTransferThresholdEth,
        BigDecimal exchangeFlowThresholdEth,
        int rapidTransferThresholdCount,
        long rapidTransferWindowMinutes,
        List<String> watchlistAddresses,
        List<String> exchangeAddresses
) {
}
