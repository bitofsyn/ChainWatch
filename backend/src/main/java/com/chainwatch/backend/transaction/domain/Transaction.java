package com.chainwatch.backend.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 66)
    private String txHash;

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
        this.txHash = txHash;
        this.fromAddress = fromAddress;
        this.toAddress = toAddress;
        this.amount = amount;
        this.gasFee = gasFee;
        this.blockNumber = blockNumber;
        this.timestamp = timestamp;
        this.contractAddress = contractAddress;
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
}
