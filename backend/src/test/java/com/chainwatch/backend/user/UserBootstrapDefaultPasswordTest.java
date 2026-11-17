package com.chainwatch.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainwatch.backend.audit.service.AuditLogService;
import com.chainwatch.backend.security.SecurityProperties;
import com.chainwatch.backend.user.bootstrap.UserBootstrapRunner;
import com.chainwatch.backend.user.domain.UserAccount;
import com.chainwatch.backend.user.domain.UserRole;
import com.chainwatch.backend.user.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

/** JWT 인증이 켜진 환경에서는 저장소에 공개된 개발 기본 비밀번호로 계정을 만들지 않는다. */
class UserBootstrapDefaultPasswordTest {

    private static final String DEV_DEFAULT = "{noop}chainwatch";

    private final UserAccountRepository repository = mock(UserAccountRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private UserBootstrapRunner runner(boolean jwtEnabled, String adminPassword, String analystPassword) {
        SecurityProperties properties = new SecurityProperties(
                jwtEnabled, "unit-test-secret", 60, 14, "admin", adminPassword, "analyst", analystPassword);
        when(repository.count()).thenReturn(0L);
        return new UserBootstrapRunner(
                repository, properties, PasswordEncoderFactories.createDelegatingPasswordEncoder(), auditLogService);
    }

    @Test
    void skipsAccountsStillUsingDevDefaultWhenJwtEnabled() {
        runner(true, "{noop}a-strong-admin-password", DEV_DEFAULT).run(new DefaultApplicationArguments());

        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("admin");
        assertThat(saved.getValue().getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void seedsNothingWhenEveryPasswordIsDevDefaultAndJwtEnabled() {
        runner(true, DEV_DEFAULT, DEV_DEFAULT).run(new DefaultApplicationArguments());

        verify(repository, never()).save(any());
    }

    @Test
    void keepsDevDefaultsWhenJwtDisabledForLocalDevelopment() {
        runner(false, DEV_DEFAULT, DEV_DEFAULT).run(new DefaultApplicationArguments());

        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(repository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(UserAccount::getUsername).containsExactly("admin", "analyst");
    }
}
