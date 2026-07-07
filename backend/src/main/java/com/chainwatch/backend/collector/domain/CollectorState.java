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

    /**
     * 마지막으로 관측한 체인 최신 블록 번호(head). confirmations = head - blockNumber + 1 계산에 쓴다.
     * 기존 데이터 마이그레이션 없이 추가하기 위해 nullable(additive)로 두며, null이면 확정 여부를 판단하지 않는다.
     * reorg rewind 시에도 되돌리지 않는다(head 관측치는 수집 진행도와 무관한 단조 증가 값).
     */
    @Column(name = "last_known_chain_head")
    private Long lastKnownChainHead;

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

    public Long getLastKnownChainHead() {
        return lastKnownChainHead;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateLastCollectedBlock(Long lastCollectedBlock, String lastCollectedBlockHash) {
        this.lastCollectedBlock = lastCollectedBlock;
        this.lastCollectedBlockHash = lastCollectedBlockHash;
        this.updatedAt = Instant.now();
    }

    /**
     * 체인 head 관측치를 갱신한다. 단조 증가만 허용해 뒤늦게 도착한 낮은 head가 확정 판정을 되돌리지 않게 한다.
     *
     * @return 값이 실제로 갱신되었으면 true
     */
    public boolean observeChainHead(long chainHead) {
        if (lastKnownChainHead != null && chainHead <= lastKnownChainHead) {
            return false;
        }
        this.lastKnownChainHead = chainHead;
        this.updatedAt = Instant.now();
        return true;
    }

    /** reorg 감지 시 되감기: 이후 수집이 rewind 지점 다음 블록부터 다시 시작된다. */
    public void rewindTo(long blockNumber) {
        this.lastCollectedBlock = blockNumber;
        this.lastCollectedBlockHash = null;
        this.updatedAt = Instant.now();
    }
}
