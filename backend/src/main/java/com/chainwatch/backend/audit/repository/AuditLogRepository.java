package com.chainwatch.backend.audit.repository;

import com.chainwatch.backend.audit.domain.AuditLog;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    long countByActionAndCreatedAtAfter(String action, Instant threshold);

    Page<AuditLog> findByActor(String actor, Pageable pageable);

    Page<AuditLog> findByAction(String action, Pageable pageable);

    Page<AuditLog> findByActorAndAction(String actor, String action, Pageable pageable);

    /** null 파라미터 분기를 명시해 DB 타입 추론 문제(:param is null)를 원천 차단한다. */
    default Page<AuditLog> search(String actor, String action, Pageable pageable) {
        boolean hasActor = actor != null && !actor.isBlank();
        boolean hasAction = action != null && !action.isBlank();
        if (hasActor && hasAction) {
            return findByActorAndAction(actor, action, pageable);
        }
        if (hasActor) {
            return findByActor(actor, pageable);
        }
        if (hasAction) {
            return findByAction(action, pageable);
        }
        return findAll(pageable);
    }
}
