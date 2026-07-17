package com.chainwatch.backend.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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

    protected Transaction() {
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
