package com.chainwatch.backend.audit.service;

import com.chainwatch.backend.audit.domain.AuditLog;
import com.chainwatch.backend.audit.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 운영자 감사 로그 기록기. 호출자와 같은 스레드/트랜잭션에서 동기 저장해
 * 상태 변경과 감사 기록이 원자적으로 함께 커밋되게 한다.
 */
@Service
public class AuditLogService {

    private static final String ANONYMOUS_ACTOR = "anonymous";
    private static final int MAX_ACTOR_LENGTH = 100;
    private static final int MAX_ROLE_LENGTH = 50;
    private static final int MAX_TARGET_ID_LENGTH = 100;
    private static final int MAX_DETAIL_LENGTH = 1000;
    private static final int MAX_CLIENT_IP_LENGTH = 64;

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /** 현재 SecurityContext의 인증 주체를 actor로 사용해 기록한다. */
    public AuditLog record(String action, String targetType, String targetId, String detail) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return record(resolveActor(authentication), resolveRole(authentication), action, targetType, targetId, detail);
    }

    /** 로그인처럼 SecurityContext가 비어 있는 시점에는 actor를 명시적으로 지정해 기록한다. */
    public AuditLog record(String actor, String role, String action, String targetType, String targetId, String detail) {
        AuditLog entry = new AuditLog(
                truncate(actor == null || actor.isBlank() ? ANONYMOUS_ACTOR : actor, MAX_ACTOR_LENGTH),
                truncate(role, MAX_ROLE_LENGTH),
                action,
                targetType,
                truncate(targetId, MAX_TARGET_ID_LENGTH),
                truncate(detail, MAX_DETAIL_LENGTH),
                truncate(resolveClientIp(), MAX_CLIENT_IP_LENGTH),
                Instant.now()
        );
        return auditLogRepository.save(entry);
    }

    private static String resolveActor(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return ANONYMOUS_ACTOR;
        }
        return authentication.getName();
    }

    private static String resolveRole(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        String roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        return roles.isBlank() ? null : roles;
    }

    private static String resolveClientIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        HttpServletRequest request = servletAttributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
