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

    // --- 분석가 workflow 필드. 전부 nullable 추가 컬럼(additive)이라 기존 데이터와 호환된다. ---

    /** 이벤트 담당 분석가 계정명 */
    @Column(length = 100)
    private String assignee;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    /** RESOLVED 종결 사유 (RESOLVED 전이 시 필수) */
    @Column(name = "resolution_reason", length = 500)
    private String resolutionReason;

    /** 오탐 판정 사유 (FALSE_POSITIVE 전이 시 필수) */
    @Column(name = "false_positive_reason", length = 500)
    private String falsePositiveReason;

    /** 조사 메모 */
    @Column(length = 2000)
    private String notes;

    // --- 룰 evidence 필드. nullable 추가 컬럼(additive)이라 기존 데이터/스키마와 호환된다. ---

    /** 발화한 룰의 버전 (예: "1.0"). 레거시 이벤트는 null. */
    @Column(name = "rule_version", length = 20)
    private String ruleVersion;

    /**
     * 룰이 발화한 이유의 구조화 JSON (rule, ruleVersion, 임계값, 관측값, 매칭 주소 등).
     * 룰별 스키마는 docs/WAVE2_BLOCKCHAIN_CONTRACTS.md 참조. 레거시 이벤트는 null.
     */
    @Column(name = "evidence", columnDefinition = "text")
    private String evidence;

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
        this.statusChangedAt = Instant.now();
    }

    public String getAssignee() {
        return assignee;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public String getResolutionReason() {
        return resolutionReason;
    }

    public String getFalsePositiveReason() {
        return falsePositiveReason;
    }

    public String getNotes() {
        return notes;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public String getEvidence() {
        return evidence;
    }

    /** 저장 전 룰 evidence를 부착한다. 기존 생성자 시그니처를 유지하기 위한 별도 메서드. */
    public void attachRuleEvidence(String ruleVersion, String evidenceJson) {
        this.ruleVersion = ruleVersion;
        this.evidence = evidenceJson;
    }

    /**
     * 분석가 workflow 상태 전이 규칙:
     * RESOLVED는 resolutionReason, FALSE_POSITIVE는 falsePositiveReason이 필수다.
     * assignee/notes는 전달된 경우에만 갱신한다(null이면 기존 값 유지).
     */
    public void applyStatusChange(
            EventStatus newStatus,
            String assignee,
            String resolutionReason,
            String falsePositiveReason,
            String notes
    ) {
        if (newStatus == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (newStatus == EventStatus.RESOLVED && isBlank(resolutionReason)) {
            throw new IllegalArgumentException("resolutionReason is required when status is RESOLVED");
        }
        if (newStatus == EventStatus.FALSE_POSITIVE && isBlank(falsePositiveReason)) {
            throw new IllegalArgumentException("falsePositiveReason is required when status is FALSE_POSITIVE");
        }
        this.status = newStatus;
        this.statusChangedAt = Instant.now();
        if (assignee != null) {
            this.assignee = assignee.isBlank() ? null : assignee;
        }
        if (resolutionReason != null) {
            this.resolutionReason = resolutionReason;
        }
        if (falsePositiveReason != null) {
            this.falsePositiveReason = falsePositiveReason;
        }
        if (notes != null) {
            this.notes = notes.isBlank() ? null : notes;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
