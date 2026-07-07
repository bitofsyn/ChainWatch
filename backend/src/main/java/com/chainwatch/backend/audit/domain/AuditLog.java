package com.chainwatch.backend.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 운영자 행위 감사 로그. 이벤트 상태 변경, 관리자 제어 액션, 로그인 성공/실패를
 * 동기적으로 기록해 "누가 언제 무엇을 했는지"를 사후 추적할 수 있게 한다.
 * 감사 레코드는 불변이며 수정/삭제 API를 제공하지 않는다.
 */
@Entity
@Table(
        name = "audit_logs",
        indexes = {
                @Index(name = "idx_audit_logs_created_at", columnList = "created_at"),
                @Index(name = "idx_audit_logs_actor", columnList = "actor"),
                @Index(name = "idx_audit_logs_action", columnList = "action")
        }
)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String actor;

    @Column(length = 50)
    private String role;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "target_type", length = 50)
    private String targetType;

    @Column(name = "target_id", length = 100)
    private String targetId;

    @Column(length = 1000)
    private String detail;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(
            String actor,
            String role,
            String action,
            String targetType,
            String targetId,
            String detail,
            String clientIp,
            Instant createdAt
    ) {
        this.actor = actor;
        this.role = role;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
        this.clientIp = clientIp;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getActor() {
        return actor;
    }

    public String getRole() {
        return role;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getDetail() {
        return detail;
    }

    public String getClientIp() {
        return clientIp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
