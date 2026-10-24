package com.chainwatch.backend.staking.reconciliation;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("chainwatch.staking.reconciliation")
public record ReconciliationProperties(boolean enabled, URI executionUrl) {
    public ReconciliationProperties {
        if (enabled && (executionUrl == null || executionUrl.getHost() == null
                || !("http".equals(executionUrl.getScheme()) || "https".equals(executionUrl.getScheme()))
                || executionUrl.getUserInfo() != null || executionUrl.getQuery() != null || executionUrl.getFragment() != null))
            throw new IllegalArgumentException("Reconciliation requires a configured execution URL");
    }
}
