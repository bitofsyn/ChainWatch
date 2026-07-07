package com.chainwatch.backend.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainwatch.backend.audit.domain.AuditLog;
import com.chainwatch.backend.audit.repository.AuditLogRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditLogServiceTest {

    private AuditLogRepository auditLogRepository;
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogRepository = mock(AuditLogRepository.class);
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        auditLogService = new AuditLogService(auditLogRepository);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsAuthenticatedActorAndRoleFromSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "alice", null, List.of(new SimpleGrantedAuthority("ROLE_ANALYST"))));

        auditLogService.record("EVENT_STATUS_CHANGE", "DETECTION_EVENT", "42", "NEW -> RESOLVED");

        AuditLog saved = capturedEntry();
        assertThat(saved.getActor()).isEqualTo("alice");
        assertThat(saved.getRole()).isEqualTo("ROLE_ANALYST");
        assertThat(saved.getAction()).isEqualTo("EVENT_STATUS_CHANGE");
        assertThat(saved.getTargetType()).isEqualTo("DETECTION_EVENT");
        assertThat(saved.getTargetId()).isEqualTo("42");
        assertThat(saved.getDetail()).isEqualTo("NEW -> RESOLVED");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void recordsAnonymousActorWhenSecurityContextIsEmpty() {
        auditLogService.record("EVENT_STATUS_CHANGE", "DETECTION_EVENT", "1", "NEW -> ACKNOWLEDGED");

        AuditLog saved = capturedEntry();
        assertThat(saved.getActor()).isEqualTo("anonymous");
        assertThat(saved.getRole()).isNull();
    }

    @Test
    void recordsExplicitActorForLoginAudit() {
        auditLogService.record("admin", "ROLE_ADMIN", "LOGIN_SUCCESS", "AUTH", "admin", "Login succeeded");

        AuditLog saved = capturedEntry();
        assertThat(saved.getActor()).isEqualTo("admin");
        assertThat(saved.getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(saved.getAction()).isEqualTo("LOGIN_SUCCESS");
    }

    @Test
    void blankExplicitActorFallsBackToAnonymous() {
        auditLogService.record("  ", null, "LOGIN_FAILURE", "AUTH", null, "Invalid username or password");

        assertThat(capturedEntry().getActor()).isEqualTo("anonymous");
    }

    @Test
    void truncatesOversizedDetail() {
        String longDetail = "x".repeat(5000);
        auditLogService.record("actor", null, "ACTION", "TYPE", "1", longDetail);

        assertThat(capturedEntry().getDetail()).hasSize(1000);
    }

    private AuditLog capturedEntry() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }
}
