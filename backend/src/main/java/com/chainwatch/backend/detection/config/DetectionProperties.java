package com.chainwatch.backend.detection.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainwatch.detection")
public record DetectionProperties(
        BigDecimal largeTransferThresholdEth
) {
}
