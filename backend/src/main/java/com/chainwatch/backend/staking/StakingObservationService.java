package com.chainwatch.backend.staking;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StakingObservationService {
    private final StakingProperties properties;
    private final BeaconObservationClient client;
    private final StakingObservationStore store;
    private final AtomicBoolean running = new AtomicBoolean();
    public StakingObservationService(StakingProperties properties, BeaconObservationClient client, StakingObservationStore store) {
        this.properties = properties; this.client = client; this.store = store;
    }
    public StakingObservation observe() {
        if (!properties.enabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Staking observation is disabled");
        if (!running.compareAndSet(false, true)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Observation is running");
        try {
            Long id = store.start(properties); // durable before network calls; interrupted processes leave RUNNING evidence
            BeaconObservationClient.Snapshot snapshot;
            try { snapshot = client.observe(); }
            catch (BeaconObservationClient.ObservationFailure failure) { return store.complete(id, null, failure.getMessage()); }
            return store.complete(id, snapshot, null);
        } finally { running.set(false); }
    }
}
