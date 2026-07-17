package com.chainwatch.backend.agentops.repository;

import com.chainwatch.backend.agentops.domain.AgentTaskFailure;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface AgentTaskFailureRepository extends JpaRepository<AgentTaskFailure, Long> {

    long countByTeamIdAndOccurredAtAfter(String teamId, Instant threshold);

    List<AgentTaskFailure> findTop4ByTeamIdOrderByOccurredAtDesc(String teamId);

    @Modifying
    long deleteByTeamIdAndInjectedTrue(String teamId);
}
