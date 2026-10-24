package com.chainwatch.backend.staking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ops/staking")
public class StakingObservationController {
    private final StakingProperties properties;
    private final StakingObservationService service;
    private final StakingObservationRepository repository;
    private final ObjectMapper mapper;
    public StakingObservationController(StakingProperties properties, StakingObservationService service,
            StakingObservationRepository repository, ObjectMapper mapper) {
        this.properties = properties; this.service = service; this.repository = repository; this.mapper = mapper;
    }
    public record ObservationView(Long id, String network, String status, Instant startedAt, Instant completedAt,
            String stateRoot, String blockRoot, String slot, String epoch, Instant stateTime,
            String clientVersion, String errorCode, boolean stale, JsonNode validators) {}
    public record Overview(boolean enabled, String network, List<String> configuredValidators, List<ObservationView> observations) {}
    @GetMapping("/observations")
    public Overview list() {
        return new Overview(properties.enabled(), properties.network(), properties.validatorIds(),
                repository.findByNetworkAndGenesisRootOrderByStartedAtDescIdDesc(properties.network(), properties.genesisValidatorsRoot(),
                        PageRequest.of(0, 20)).stream().map(this::view).toList());
    }
    @PostMapping("/observations")
    public ObservationView observe() { return view(service.observe()); }
    @GetMapping("/observations/{id}/evidence")
    public JsonNode evidence(@PathVariable Long id) {
        var run = repository.findById(id).filter(r -> r.getNetwork().equals(properties.network())
                && r.getGenesisRoot().equals(properties.genesisValidatorsRoot())).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return json(run.getRawEvidence());
    }
    private ObservationView view(StakingObservation run) {
        Instant evidenceTime = run.getCheckpointObservedAt() == null ? run.getStartedAt() : run.getCheckpointObservedAt();
        return new ObservationView(run.getId(), run.getNetwork(), run.getStatus(), run.getStartedAt(), run.getCompletedAt(),
                run.getStateRoot(), run.getBlockRoot(), run.getSlot(), run.getEpoch(), run.getStateTime(), run.getClientVersion(), run.getErrorCode(),
                evidenceTime.plus(properties.staleAfter()).isBefore(Instant.now())
                        || (run.getStateTime() != null && run.getStateTime().plus(properties.maxCheckpointAge()).isBefore(Instant.now())), json(run.getValidatorJson()));
    }
    private JsonNode json(String value) {
        if (value == null) return mapper.nullNode();
        try { return mapper.readTree(value); }
        catch (java.io.IOException e) { throw new IllegalStateException("Invalid stored observation", e); }
    }
}
