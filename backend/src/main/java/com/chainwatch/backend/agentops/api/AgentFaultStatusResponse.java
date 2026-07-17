package com.chainwatch.backend.agentops.api;

import java.time.Instant;

/** 팀별 장애 주입 상태. active=false면 activatedAt/expiresAt은 null이다. */
public record AgentFaultStatusResponse(
        String teamId,
        boolean active,
        String scenario,
        Instant activatedAt,
        Instant expiresAt
) {
}
