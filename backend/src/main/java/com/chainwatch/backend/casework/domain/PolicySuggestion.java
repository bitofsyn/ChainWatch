package com.chainwatch.backend.casework.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 분석가가 관리자에게 올리는 탐지 예외 주소 제안. 오탐으로 판단한 주소(거래소 지갑, 배치 지급 주소 등)를
 * 이후 탐지에서 제외해 달라고 요청하고, 관리자가 승인하면 탐지 엔진이 그 주소의 이벤트를 만들지 않는다.
 */
@Entity
@Table(
        name = "policy_suggestions",
        indexes = {
                @Index(name = "idx_policy_suggestions_status", columnList = "status"),
                @Index(name = "idx_policy_suggestions_address", columnList = "address")
        }
)
public class PolicySuggestion {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_REJECTED = "REJECTED";
    /** 승인됐던 예외를 관리자가 해제한 상태 */
    public static final String STATUS_REVOKED = "REVOKED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String address;

    @Column(nullable = false, length = 500)
    private String reason;

    /** 제안의 근거가 된 이벤트(없을 수 있음) */
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "proposed_by", nullable = false, length = 100)
    private String proposedBy;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "decided_by", length = 100)
    private String decidedBy;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected PolicySuggestion() {
    }

    public PolicySuggestion(String address, String reason, Long eventId, String proposedBy) {
        this.address = address.toLowerCase();
        this.reason = reason;
        this.eventId = eventId;
        this.proposedBy = proposedBy;
        this.status = STATUS_PENDING;
        this.createdAt = Instant.now();
    }

    public void decide(String newStatus, String decidedBy, String note) {
        this.status = newStatus;
        this.decidedBy = decidedBy;
        this.decisionNote = note == null || note.isBlank() ? null : note;
        this.decidedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getAddress() {
        return address;
    }

    public String getReason() {
        return reason;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getProposedBy() {
        return proposedBy;
    }

    public String getStatus() {
        return status;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
