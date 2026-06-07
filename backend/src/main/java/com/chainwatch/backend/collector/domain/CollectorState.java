package com.chainwatch.backend.collector.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "collector_state")
public class CollectorState {

    @Id
    @Column(length = 50, nullable = false)
    private String collectorName;

    @Column(nullable = false)
    private Long lastCollectedBlock;

    @Column(nullable = false)
    private Instant updatedAt;

    protected CollectorState() {
    }

    public CollectorState(String collectorName, Long lastCollectedBlock, Instant updatedAt) {
        this.collectorName = collectorName;
        this.lastCollectedBlock = lastCollectedBlock;
        this.updatedAt = updatedAt;
    }

    public String getCollectorName() {
        return collectorName;
    }

    public Long getLastCollectedBlock() {
        return lastCollectedBlock;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateLastCollectedBlock(Long lastCollectedBlock) {
        this.lastCollectedBlock = lastCollectedBlock;
        this.updatedAt = Instant.now();
    }
}
