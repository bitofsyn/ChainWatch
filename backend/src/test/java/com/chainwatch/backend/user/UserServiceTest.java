package com.chainwatch.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chainwatch.backend.auth.exception.InvalidRefreshTokenException;
import com.chainwatch.backend.auth.service.RefreshTokenService;
import com.chainwatch.backend.common.exception.ConflictException;
import com.chainwatch.backend.user.api.PasswordResetRequest;
import com.chainwatch.backend.user.api.UserCreateRequest;
import com.chainwatch.backend.user.api.UserCreateResponse;
import com.chainwatch.backend.user.api.UserUpdateRequest;
import com.chainwatch.backend.user.domain.UserAccount;
import com.chainwatch.backend.user.domain.UserRole;
import com.chainwatch.backend.user.repository.UserAccountRepository;
import com.chainwatch.backend.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 관리 서비스 규칙: 마지막 활성 ADMIN 보호, 자기 비활성화 금지, 세션 폐기. */
@SpringBootTest(properties = {
        // 컨텍스트 캐시 분리용 고유 속성(공유 컨텍스트의 사용자 데이터 오염 방지)
        "chainwatch.security.refresh-expiration-days=15"
})
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    private UserAccount seededAdmin() {
        return userAccountRepository.findByUsername("admin").orElseThrow();
    }

    @Test
    void createRejectsDuplicateUsername() {
        assertThatThrownBy(() -> userService.create(new UserCreateRequest("admin", UserRole.ANALYST, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createWithoutPasswordGeneratesOne() {
        UserCreateResponse response =
                userService.create(new UserCreateRequest("svc-gen", UserRole.ANALYST, "생성 테스트", null));

        assertThat(response.initialPassword()).hasSize(16);
        assertThat(response.user().role()).isEqualTo("ANALYST");
    }

    @Test
    void lastActiveAdminCannotBeDeactivatedOrDemoted() {
        Long adminId = seededAdmin().getId();

        assertThatThrownBy(() -> userService.update(adminId,
                new UserUpdateRequest(null, null, false), "someone-else"))
                .isInstanceOf(ConflictException.class);

        assertThatThrownBy(() -> userService.update(adminId,
                new UserUpdateRequest(UserRole.ANALYST, null, null), "someone-else"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void adminCannotDeactivateSelfEvenWithAnotherAdminPresent() {
        userService.create(new UserCreateRequest("svc-admin2", UserRole.ADMIN, null, "password123!"));
        Long adminId = seededAdmin().getId();

        assertThatThrownBy(() -> userService.update(adminId,
                new UserUpdateRequest(null, null, false), "admin"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deactivationRevokesAllRefreshTokens() {
        UserCreateResponse created =
                userService.create(new UserCreateRequest("svc-deact", UserRole.ANALYST, null, "password123!"));
        UserAccount user = userAccountRepository.findByUsername("svc-deact").orElseThrow();
        String rawRefreshToken = refreshTokenService.issue(user);

        userService.update(created.user().id(), new UserUpdateRequest(null, null, false), "admin");

        assertThatThrownBy(() -> refreshTokenService.rotate(rawRefreshToken))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void secondActiveAdminAllowsDeactivatingTheFirst() {
        userService.create(new UserCreateRequest("svc-admin3", UserRole.ADMIN, null, "password123!"));
        Long adminId = seededAdmin().getId();

        userService.update(adminId, new UserUpdateRequest(null, null, false), "svc-admin3");

        assertThat(seededAdmin().isActive()).isFalse();
    }

    @Test
    void passwordResetRevokesSessionsAndReturnsGeneratedPassword() {
        UserCreateResponse created =
                userService.create(new UserCreateRequest("svc-reset", UserRole.ANALYST, null, "password123!"));
        UserAccount user = userAccountRepository.findByUsername("svc-reset").orElseThrow();
        String rawRefreshToken = refreshTokenService.issue(user);

        UserCreateResponse reset = userService.resetPassword(created.user().id(), new PasswordResetRequest(null));

        assertThat(reset.initialPassword()).hasSize(16);
        assertThatThrownBy(() -> refreshTokenService.rotate(rawRefreshToken))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }
}
