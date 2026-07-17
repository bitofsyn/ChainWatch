package com.chainwatch.backend.agentops.api;

import com.chainwatch.backend.agentops.api.AgentOpsSnapshotResponse.Handoff;
import com.chainwatch.backend.agentops.api.AgentOpsSnapshotResponse.Team;
import com.chainwatch.backend.agentops.service.AgentFaultInjector;
import com.chainwatch.backend.agentops.service.AgentFaultService;
import com.chainwatch.backend.agentops.service.AgentOpsService;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent-ops")
public class AgentOpsController {

    private final AgentOpsService agentOpsService;
    private final AgentFaultService agentFaultService;

    public AgentOpsController(AgentOpsService agentOpsService, AgentFaultService agentFaultService) {
        this.agentOpsService = agentOpsService;
        this.agentFaultService = agentFaultService;
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

    @GetMapping("/faults")
    public List<AgentFaultStatusResponse> faults() {
        return agentFaultService.statuses();
    }

    @PostMapping("/faults/{teamId}")
    public AgentFaultStatusResponse activateFault(
            @PathVariable String teamId,
            @RequestParam(defaultValue = "" + AgentFaultInjector.DEFAULT_TTL_SECONDS) long ttlSeconds,
            @RequestParam(defaultValue = "3") int drillCount
    ) {
        return agentFaultService.activate(teamId, ttlSeconds, drillCount);
    }

    @DeleteMapping("/faults/{teamId}")
    public AgentFaultStatusResponse clearFault(
            @PathVariable String teamId,
            @RequestParam(defaultValue = "true") boolean purge
    ) {
        return agentFaultService.clear(teamId, purge);
    }
}
