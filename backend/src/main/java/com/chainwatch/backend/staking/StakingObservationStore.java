package com.chainwatch.backend.staking;

import com.chainwatch.backend.audit.service.AuditLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StakingObservationStore {
    private final StakingObservationRepository repository;
    private final AuditLogService audit;
    private final ObjectMapper mapper;
    public StakingObservationStore(StakingObservationRepository repository, AuditLogService audit, ObjectMapper mapper) {
        this.repository = repository; this.audit = audit; this.mapper = mapper;
    }
    @Transactional
    public Long start(StakingProperties properties) {
        var run = repository.save(new StakingObservation(properties.network(), properties.genesisValidatorsRoot()));
        audit.record("STAKING_OBSERVATION_STARTED", "STAKING_OBSERVATION", run.getId().toString(), "Configured validators observation");
        return run.getId();
    }
    @Transactional
    public StakingObservation complete(Long id, BeaconObservationClient.Snapshot snapshot, String error) {
        var run = repository.findById(id).orElseThrow();
        if (snapshot == null) run.fail(error);
        else {
            try { run.complete(snapshot, mapper.writeValueAsString(snapshot.validators())); }
            catch (JsonProcessingException e) { throw new IllegalStateException("Cannot encode validators", e); }
        }
        audit.record("STAKING_OBSERVATION_COMPLETED", "STAKING_OBSERVATION", id.toString(), run.getStatus());
        return run;
    }
}
