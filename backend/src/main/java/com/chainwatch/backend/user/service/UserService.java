package com.chainwatch.backend.user.service;

import com.chainwatch.backend.audit.service.AuditLogService;
import com.chainwatch.backend.auth.service.RefreshTokenService;
import com.chainwatch.backend.common.exception.ConflictException;
import com.chainwatch.backend.common.exception.ResourceNotFoundException;
import com.chainwatch.backend.user.api.PasswordResetRequest;
import com.chainwatch.backend.user.api.UserCreateRequest;
import com.chainwatch.backend.user.api.UserCreateResponse;
import com.chainwatch.backend.user.api.UserResponse;
import com.chainwatch.backend.user.api.UserUpdateRequest;
import com.chainwatch.backend.user.domain.UserAccount;
import com.chainwatch.backend.user.domain.UserRole;
import com.chainwatch.backend.user.repository.UserAccountRepository;
import java.security.SecureRandom;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADMIN 전용 사용자 계정 관리. 모든 변이는 감사 로그를 남긴다.
 * 마지막 활성 ADMIN은 강등·비활성화할 수 없다(콘솔 잠금 방지).
 */
@Service
public class UserService {

    private static final String AUDIT_TARGET_TYPE_USER = "USER";
    private static final String PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
    private static final int GENERATED_PASSWORD_LENGTH = 16;

    private final UserAccountRepository userAccountRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserService(
            UserAccountRepository userAccountRepository,
            RefreshTokenService refreshTokenService,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userAccountRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public UserCreateResponse create(UserCreateRequest request) {
        if (userAccountRepository.existsByUsername(request.username())) {
            throw new ConflictException("이미 존재하는 사용자명입니다: " + request.username());
        }
        boolean generated = request.initialPassword() == null || request.initialPassword().isBlank();
        String rawPassword = generated ? generatePassword() : request.initialPassword();
        UserAccount user = userAccountRepository.save(new UserAccount(
                request.username(), passwordEncoder.encode(rawPassword), request.role(), request.displayName()));
        auditLogService.record("USER_CREATE", AUDIT_TARGET_TYPE_USER, user.getUsername(),
                "Created with role " + user.getRole());
        return new UserCreateResponse(UserResponse.from(user), generated ? rawPassword : null);
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request, String actorUsername) {
        UserAccount user = requireUser(id);
        StringBuilder changes = new StringBuilder();

        if (request.role() != null && request.role() != user.getRole()) {
            if (user.getRole() == UserRole.ADMIN) {
                requireAnotherActiveAdmin(user, "마지막 활성 ADMIN의 역할은 변경할 수 없습니다.");
            }
            appendChange(changes, "role " + user.getRole() + "→" + request.role());
            user.changeRole(request.role());
        }
        if (request.displayName() != null && !request.displayName().equals(user.getDisplayName())) {
            appendChange(changes, "displayName");
            user.changeDisplayName(request.displayName());
        }
        if (request.active() != null && request.active() != user.isActive()) {
            if (!request.active()) {
                if (user.getUsername().equals(actorUsername)) {
                    throw new ConflictException("자기 자신은 비활성화할 수 없습니다.");
                }
                if (user.getRole() == UserRole.ADMIN) {
                    requireAnotherActiveAdmin(user, "마지막 활성 ADMIN은 비활성화할 수 없습니다.");
                }
                user.deactivate();
                refreshTokenService.revokeAllForUser(user);
                appendChange(changes, "deactivated (all sessions revoked)");
                auditLogService.record("USER_DEACTIVATE", AUDIT_TARGET_TYPE_USER, user.getUsername(), changes.toString());
                return UserResponse.from(user);
            }
            user.activate();
            appendChange(changes, "activated");
        }

        if (!changes.isEmpty()) {
            auditLogService.record("USER_UPDATE", AUDIT_TARGET_TYPE_USER, user.getUsername(), changes.toString());
        }
        return UserResponse.from(user);
    }

    /** ADMIN의 비밀번호 초기화. 대상 사용자의 모든 세션을 폐기한다. */
    @Transactional
    public UserCreateResponse resetPassword(Long id, PasswordResetRequest request) {
        UserAccount user = requireUser(id);
        boolean generated = request.newPassword() == null || request.newPassword().isBlank();
        String rawPassword = generated ? generatePassword() : request.newPassword();
        user.changePassword(passwordEncoder.encode(rawPassword));
        refreshTokenService.revokeAllForUser(user);
        auditLogService.record("USER_PASSWORD_RESET", AUDIT_TARGET_TYPE_USER, user.getUsername(),
                "Password reset by admin; all sessions revoked");
        return new UserCreateResponse(UserResponse.from(user), generated ? rawPassword : null);
    }

    private UserAccount requireUser(Long id) {
        return userAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다: id=" + id));
    }

    private void requireAnotherActiveAdmin(UserAccount target, String message) {
        boolean targetCountsAsActiveAdmin = target.getRole() == UserRole.ADMIN && target.isActive();
        long activeAdmins = userAccountRepository.countByRoleAndActiveTrue(UserRole.ADMIN);
        if (targetCountsAsActiveAdmin && activeAdmins <= 1) {
            throw new ConflictException(message);
        }
    }

    private static void appendChange(StringBuilder changes, String change) {
        if (!changes.isEmpty()) {
            changes.append(", ");
        }
        changes.append(change);
    }

    private String generatePassword() {
        StringBuilder password = new StringBuilder(GENERATED_PASSWORD_LENGTH);
        for (int i = 0; i < GENERATED_PASSWORD_LENGTH; i++) {
            password.append(PASSWORD_CHARS.charAt(secureRandom.nextInt(PASSWORD_CHARS.length())));
        }
        return password.toString();
    }
}
