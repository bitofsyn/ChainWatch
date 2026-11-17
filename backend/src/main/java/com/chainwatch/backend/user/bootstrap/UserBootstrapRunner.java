package com.chainwatch.backend.user.bootstrap;

import com.chainwatch.backend.audit.service.AuditLogService;
import com.chainwatch.backend.security.SecurityProperties;
import com.chainwatch.backend.user.domain.UserAccount;
import com.chainwatch.backend.user.domain.UserRole;
import com.chainwatch.backend.user.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최초 기동 시딩. user_accounts가 비어 있을 때만 chainwatch.security.admin-*(필수)와
 * analyst-*(선택) 속성으로 초기 계정을 만든다. 사용자가 하나라도 있으면 아무것도 하지 않으므로
 * 재기동에 멱등하며, 이후 계정 관리는 전적으로 /api/users API를 통한다(속성 변경은 무시됨).
 *
 * JWT 인증이 켜진 환경에서 비밀번호가 개발 기본값({noop}chainwatch) 그대로인 계정은 만들지 않는다.
 *
 * {noop} 형식 비밀번호(로컬 개발 기본값)와 인코딩 접두사가 없는 값은 평문으로 보고 BCrypt로 인코딩한다.
 * {bcrypt}... 등 다른 접두사가 붙은 값은 이미 인코딩된 것으로 간주해 그대로 저장한다.
 *
 * 과거 버전은 접두사 없는 값을 그대로 저장해 해당 계정으로 로그인할 수 없었다. 기동 시 그런 계정을 찾아
 * 저장된 값(=입력했던 평문)을 BCrypt로 다시 인코딩해 복구한다.
 */
@Component
@Order(10)
public class UserBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserBootstrapRunner.class);

    private static final String NOOP_PREFIX = "{noop}";

    /** application.yml의 로컬 개발용 기본 비밀번호. 공개 저장소에 노출된 값이다. */
    private static final String DEFAULT_DEV_PASSWORD = "{noop}chainwatch";

    private final UserAccountRepository userAccountRepository;
    private final SecurityProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public UserBootstrapRunner(
            UserAccountRepository userAccountRepository,
            SecurityProperties properties,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        repairUnencodedPasswords();
        if (userAccountRepository.count() > 0) {
            return;
        }

        StringBuilder seeded = new StringBuilder();
        seed(properties.adminUsername(), properties.adminPassword(), UserRole.ADMIN, "운영 관리자", seeded);
        seed(properties.analystUsername(), properties.analystPassword(), UserRole.ANALYST, "탐지 분석가", seeded);

        if (seeded.isEmpty()) {
            log.warn("[USER_BOOTSTRAP] user_accounts is empty but no seed credentials are configured "
                    + "(chainwatch.security.admin-username/admin-password). No user can log in.");
            return;
        }

        log.warn("[USER_BOOTSTRAP] seeded initial accounts from chainwatch.security properties: {}. "
                + "These properties are bootstrap-only; manage users via /api/users from now on.", seeded);
        auditLogService.record("system", null, "USER_BOOTSTRAP", "USER", seeded.toString(),
                "Seeded initial accounts because user_accounts was empty");
    }

    /** 인코딩 접두사 없이 저장된 비밀번호(과거 버전이 평문을 그대로 저장한 경우)를 BCrypt로 복구한다. */
    private void repairUnencodedPasswords() {
        for (UserAccount account : userAccountRepository.findAll()) {
            String stored = account.getPasswordHash();
            if (stored == null || stored.isBlank() || hasEncodingPrefix(stored)) {
                continue;
            }
            account.changePassword(passwordEncoder.encode(stored), account.getCurrentPasswordFingerprint());
            userAccountRepository.save(account);
            log.warn("[USER_BOOTSTRAP] re-encoded the password of '{}': it was stored without an encoding prefix "
                    + "and could not be verified at login.", account.getUsername());
            auditLogService.record("system", null, "USER_PASSWORD_REPAIR", "USER", account.getUsername(),
                    "Re-encoded a password stored without an encoding prefix");
        }
    }

    /** DelegatingPasswordEncoder 형식({id}hash) 여부. */
    private static boolean hasEncodingPrefix(String value) {
        return value.startsWith("{") && value.indexOf('}') > 1;
    }

    private void seed(String username, String encodedPassword, UserRole role, String displayName, StringBuilder seeded) {
        if (username == null || username.isBlank() || encodedPassword == null || encodedPassword.isBlank()) {
            return;
        }
        // JWT 인증을 켠 환경은 외부 공개를 전제로 한다. 저장소에 공개된 기본 비밀번호로는 계정을 만들지 않는다.
        if (properties.jwtEnabled() && DEFAULT_DEV_PASSWORD.equals(encodedPassword)) {
            log.warn("[USER_BOOTSTRAP] skipped seeding '{}' ({}): its password is still the built-in development "
                    + "default while JWT security is enabled. Set a strong password to create this account.",
                    username, role);
            return;
        }
        String passwordHash;
        if (encodedPassword.startsWith(NOOP_PREFIX)) {
            passwordHash = passwordEncoder.encode(encodedPassword.substring(NOOP_PREFIX.length()));
        } else if (!hasEncodingPrefix(encodedPassword)) {
            // 접두사 없이 입력한 값은 평문이다. 그대로 저장하면 로그인 검증이 불가능하다.
            passwordHash = passwordEncoder.encode(encodedPassword);
        } else {
            passwordHash = encodedPassword;
        }
        userAccountRepository.save(new UserAccount(username, passwordHash, role, displayName));
        if (!seeded.isEmpty()) {
            seeded.append(", ");
        }
        seeded.append(username).append("(").append(role).append(")");
    }
}
