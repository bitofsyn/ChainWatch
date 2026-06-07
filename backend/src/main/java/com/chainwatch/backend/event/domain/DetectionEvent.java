package com.chainwatch.backend.event.domain;

import com.chainwatch.backend.transaction.domain.Transaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "detection_events")
public class DetectionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskLevel riskLevel;

    @Column(nullable = false)
    private Integer riskScore;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(nullable = false, length = 100)
    private String walletAddress;

    @Column(nullable = false)
    private Instant detectedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    protected DetectionEvent() {
    }

    public DetectionEvent(
            EventType eventType,
            RiskLevel riskLevel,
            Integer riskScore,
            String summary,
            String walletAddress,
            Instant detectedAt,
            Transaction transaction
    ) {
        this.eventType = eventType;
        this.riskLevel = riskLevel;
        this.riskScore = riskScore;
        this.summary = summary;
        this.walletAddress = walletAddress;
        this.detectedAt = detectedAt;
        this.transaction = transaction;
    }

    public Long getId() {
        return id;
    }

    public EventType getEventType() {
        return eventType;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public String getSummary() {
        return summary;
    }

    public String getWalletAddress() {
        return walletAddress;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public Transaction getTransaction() {
        return transaction;
    }
}
