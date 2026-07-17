package com.chainwatch.backend.agentops.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 팀별 장애 주입 상태를 인메모리로 관리한다.
 * 외부 인프라(Kafka/Redis) 의존이 없어 어떤 실행 환경에서도 동일하게 동작하며,
 * TTL이 지나면 자동 해제되어 주입 장애가 방치되지 않는다.
 * (인스턴스 로컬 상태이므로 다중 인스턴스 배포에서는 인스턴스별로 주입해야 한다.)
 */
@Component
public class AgentFaultInjector {

    /** 컨트롤러 @RequestParam defaultValue에서 상수 식으로 쓰이므로 리터럴로 유지한다. */
    public static final long DEFAULT_TTL_SECONDS = 600;
    private static final long MIN_TTL_SECONDS = 60;
    private static final long MAX_TTL_SECONDS = Duration.ofHours(1).toSeconds();

    public record FaultState(String teamId, String scenario, Instant activatedAt, Instant expiresAt) {
    }

    private final Map<String, FaultState> states = new ConcurrentHashMap<>();

    public FaultState activate(String teamId, String scenario, long ttlSeconds) {
        long ttl = Math.max(MIN_TTL_SECONDS, Math.min(ttlSeconds, MAX_TTL_SECONDS));
        Instant now = Instant.now();
        FaultState state = new FaultState(teamId, scenario, now, now.plusSeconds(ttl));
        states.put(teamId, state);
        return state;
    }

    public void clear(String teamId) {
        states.remove(teamId);
    }

    public boolean isActive(String teamId) {
        return state(teamId).isPresent();
    }

    /** 만료된 상태는 조회 시점에 제거해 항상 유효한 상태만 노출한다. */
    public Optional<FaultState> state(String teamId) {
        FaultState state = states.get(teamId);
        if (state == null) {
            return Optional.empty();
        }
        if (state.expiresAt().isBefore(Instant.now())) {
            states.remove(teamId, state);
            return Optional.empty();
        }
        return Optional.of(state);
    }
}
