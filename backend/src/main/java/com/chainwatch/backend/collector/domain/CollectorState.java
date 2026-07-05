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

    /** 마지막으로 수집한 블록의 해시. 다음 블록의 parentHash와 비교해 reorg를 감지한다. */
    @Column(length = 66)
    private String lastCollectedBlockHash;

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

    public String getLastCollectedBlockHash() {
        return lastCollectedBlockHash;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateLastCollectedBlock(Long lastCollectedBlock, String lastCollectedBlockHash) {
        this.lastCollectedBlock = lastCollectedBlock;
        this.lastCollectedBlockHash = lastCollectedBlockHash;
        this.updatedAt = Instant.now();
    }

    /** reorg 감지 시 되감기: 이후 수집이 rewind 지점 다음 블록부터 다시 시작된다. */
    public void rewindTo(long blockNumber) {
        this.lastCollectedBlock = blockNumber;
        this.lastCollectedBlockHash = null;
        this.updatedAt = Instant.now();
    }
}
