package com.chainwatch.backend.audit.api;

import com.chainwatch.backend.audit.domain.AuditLog;
import java.time.Instant;

public record AuditLogResponse(
        Long id,
        String actor,
        String role,
        String action,
        String targetType,
        String targetId,
        String detail,
        String clientIp,
        Instant createdAt
) {
    public static AuditLogResponse from(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getActor(),
                auditLog.getRole(),
                auditLog.getAction(),
                auditLog.getTargetType(),
                auditLog.getTargetId(),
                auditLog.getDetail(),
                auditLog.getClientIp(),
                auditLog.getCreatedAt()
        );
    }
}
