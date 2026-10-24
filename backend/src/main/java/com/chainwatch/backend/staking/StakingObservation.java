package com.chainwatch.backend.staking;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "staking_observations", indexes = @Index(name = "idx_staking_network_started", columnList = "network,started_at"))
public class StakingObservation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 80) private String network;
    @Column(nullable = false, length = 66) private String genesisRoot;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    private Instant completedAt;
    private Instant checkpointObservedAt;
    private Instant stateTime;
    @Column(length = 66) private String blockRoot;
    @Column(length = 20) private String slot;
    @Column(length = 20) private String epoch;
    @Column(length = 66) private String stateRoot;
    @Column(length = 250) private String clientVersion;
    @Column(length = 80) private String errorCode;
    @Column(columnDefinition = "text") private String validatorJson;
    @Column(columnDefinition = "text") private String rawEvidence;
    protected StakingObservation() {}
    public StakingObservation(String network, String genesisRoot) {
        this.network = network; this.genesisRoot = genesisRoot; status = "RUNNING"; startedAt = Instant.now();
    }
    public void complete(BeaconObservationClient.Snapshot snapshot, String validators) {
        status = "OBSERVED"; stateRoot = snapshot.stateRoot(); clientVersion = snapshot.clientVersion();
        checkpointObservedAt = snapshot.checkpointObservedAt(); stateTime = snapshot.stateTime();
        blockRoot = snapshot.blockRoot(); slot = snapshot.slot(); epoch = snapshot.epoch(); validatorJson = validators;
        rawEvidence = snapshot.rawEvidence(); completedAt = Instant.now();
    }
    public void fail(String code) { status = "INCOMPLETE"; errorCode = code; completedAt = Instant.now(); }
    public Long getId() { return id; }
    public String getNetwork() { return network; }
    public String getGenesisRoot() { return genesisRoot; }
    public String getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCheckpointObservedAt() { return checkpointObservedAt; }
    public Instant getStateTime() { return stateTime; }
    public String getBlockRoot() { return blockRoot; }
    public String getSlot() { return slot; }
    public String getEpoch() { return epoch; }
    public String getStateRoot() { return stateRoot; }
    public String getClientVersion() { return clientVersion; }
    public String getErrorCode() { return errorCode; }
    public String getValidatorJson() { return validatorJson; }
    public String getRawEvidence() { return rawEvidence; }
}
