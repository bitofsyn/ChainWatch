package com.chainwatch.backend.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "transactions",
        indexes = {
                @Index(name = "idx_transactions_from_address", columnList = "from_address"),
                @Index(name = "idx_transactions_to_address", columnList = "to_address"),
                @Index(name = "idx_transactions_block_number", columnList = "block_number"),
                @Index(name = "idx_transactions_timestamp", columnList = "timestamp"),
                @Index(name = "idx_transactions_collected_at", columnList = "collected_at"),
                @Index(name = "idx_transactions_network", columnList = "network")
        }
)
public class Transaction {

    /** 멀티체인 이전 데이터 및 network 미지정 시의 기본 체인. */
    public static final String DEFAULT_NETWORK = "ethereum-mainnet";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 66)
    private String txHash;

    /** 트랜잭션이 관측된 체인(예: ethereum-mainnet, polygon-mainnet). 레거시 행은 null → 기본 체인. */
    @Column(length = 50)
    private String network;

    @Column(nullable = false, length = 100)
    private String fromAddress;

    @Column(nullable = false, length = 100)
    private String toAddress;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal amount;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal gasFee;

    @Column(nullable = false)
    private Long blockNumber;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(length = 100)
    private String contractAddress;

    /**
     * 수집기가 이 거래를 저장한 시각. 블록 시각({@link #timestamp})과 달리 수집이 밀려도 실제로 처리한
     * 시점을 가리키므로, 대시보드 처리량·탐지율의 기준으로 쓴다. 이 컬럼이 생기기 전 행은 null이다.
     */
    @Column(name = "collected_at")
    private Instant collectedAt;

    protected Transaction() {
    }

    @PrePersist
    void markCollected() {
        if (collectedAt == null) {
            collectedAt = Instant.now();
        }
    }

    /** 체인 미지정 하위호환 생성자. network는 기본 체인으로 설정된다. */
    public Transaction(
            String txHash,
            String fromAddress,
            String toAddress,
            BigDecimal amount,
            BigDecimal gasFee,
            Long blockNumber,
            Instant timestamp,
            String contractAddress
    ) {
        this(txHash, fromAddress, toAddress, amount, gasFee, blockNumber, timestamp, contractAddress, DEFAULT_NETWORK);
    }

    public Transaction(
            String txHash,
            String fromAddress,
            String toAddress,
            BigDecimal amount,
            BigDecimal gasFee,
            Long blockNumber,
            Instant timestamp,
            String contractAddress,
            String network
    ) {
        this.txHash = txHash;
        this.fromAddress = fromAddress;
        this.toAddress = toAddress;
        this.amount = amount;
        this.gasFee = gasFee;
        this.blockNumber = blockNumber;
        this.timestamp = timestamp;
        this.contractAddress = contractAddress;
        this.network = (network == null || network.isBlank()) ? DEFAULT_NETWORK : network;
    }

    public Long getId() {
        return id;
    }

    public Instant getCollectedAt() {
        return collectedAt;
    }

    public String getTxHash() {
        return txHash;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public String getToAddress() {
        return toAddress;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getGasFee() {
        return gasFee;
    }

    public Long getBlockNumber() {
        return blockNumber;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getContractAddress() {
        return contractAddress;
    }

    /** 레거시 행(null)은 기본 체인으로 간주한다. */
    public String getNetwork() {
        return network == null || network.isBlank() ? DEFAULT_NETWORK : network;
    }
}
