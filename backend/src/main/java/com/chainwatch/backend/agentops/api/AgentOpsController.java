package com.chainwatch.backend.agentops.api;

import com.chainwatch.backend.agentops.api.AgentOpsSnapshotResponse.Handoff;
import com.chainwatch.backend.agentops.api.AgentOpsSnapshotResponse.Team;
import com.chainwatch.backend.agentops.service.AgentOpsService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent-ops")
public class AgentOpsController {

    private final AgentOpsService agentOpsService;

    public AgentOpsController(AgentOpsService agentOpsService) {
        this.agentOpsService = agentOpsService;
    }

    @GetMapping("/snapshot")
    public AgentOpsSnapshotResponse snapshot() {
        return agentOpsService.snapshot();
    }

    @GetMapping("/teams/{teamId}")
    public Team team(@PathVariable String teamId) {
        return agentOpsService.team(teamId);
    }

    @GetMapping("/handoffs")
    public List<Handoff> handoffs(@RequestParam(defaultValue = "50") int limit) {
        return agentOpsService.handoffs(Math.max(1, Math.min(limit, 200)));
    }
}
