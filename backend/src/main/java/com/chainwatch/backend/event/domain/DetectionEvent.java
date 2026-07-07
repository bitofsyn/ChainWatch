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
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * (transaction_id, event_type) 유니크 제약은 동시성/재처리/멀티 인스턴스 환경에서
 * 애플리케이션 레벨 exists 체크가 놓치는 중복 저장을 DB 레벨에서 차단한다.
 */
@Entity
@Table(
        name = "detection_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_detection_events_transaction_event_type",
                columnNames = {"transaction_id", "event_type"}
        ),
        indexes = {
                @Index(name = "idx_detection_events_detected_at", columnList = "detected_at"),
                @Index(name = "idx_detection_events_risk_level", columnList = "risk_level"),
                @Index(name = "idx_detection_events_wallet_address", columnList = "wallet_address"),
                @Index(name = "idx_detection_events_status", columnList = "status"),
                @Index(name = "idx_detection_events_event_type", columnList = "event_type")
        }
)
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

    /** 기존 데이터 마이그레이션 없이 컬럼을 추가하기 위해 nullable로 두고, null은 NEW로 간주한다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private EventStatus status;

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

    public EventStatus getStatus() {
        return status != null ? status : EventStatus.NEW;
    }

    public void changeStatus(EventStatus status) {
        this.status = status;
    }
}
