package com.chainwatch.backend.staking;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("chainwatch.staking")
public record StakingProperties(boolean enabled, String network, URI beaconUrl,
        String genesisValidatorsRoot, List<String> validatorIds, Duration requestTimeout, Duration staleAfter, Duration maxCheckpointAge) {
    public StakingProperties {
        network = network == null ? "unconfigured" : network;
        validatorIds = validatorIds == null ? List.of() : List.copyOf(validatorIds);
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(5) : requestTimeout;
        staleAfter = staleAfter == null ? Duration.ofMinutes(15) : staleAfter;
        maxCheckpointAge = maxCheckpointAge == null ? Duration.ofMinutes(30) : maxCheckpointAge;
        if (maxCheckpointAge.isNegative() || maxCheckpointAge.isZero()) throw new IllegalArgumentException("Checkpoint age must be positive");
        if (requestTimeout.isNegative() || requestTimeout.isZero() || staleAfter.isNegative() || staleAfter.isZero())
            throw new IllegalArgumentException("Staking durations must be positive");
        if (validatorIds.size() > 20 || validatorIds.stream().distinct().count() != validatorIds.size()
                || validatorIds.stream().anyMatch(id -> !id.matches("[0-9]{1,20}|0x[0-9a-fA-F]{96}")))
            throw new IllegalArgumentException("Configure up to 20 distinct validator indices or public keys");
        if (enabled && (network.isBlank() || network.length() > 80 || beaconUrl == null
                || !("https".equals(beaconUrl.getScheme()) || "http".equals(beaconUrl.getScheme()))
                || beaconUrl.getHost() == null || beaconUrl.getUserInfo() != null
                || beaconUrl.getQuery() != null || beaconUrl.getFragment() != null
                || genesisValidatorsRoot == null || !genesisValidatorsRoot.matches("0x[0-9a-fA-F]{64}")
                || validatorIds.isEmpty()))
            throw new IllegalArgumentException("Enabled staking requires network, Beacon URL, genesis root and validators");
    }
}
