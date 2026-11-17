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
 * {noop} 형식 비밀번호(로컬 개발 기본값)는 DB에 평문 마커로 남기지 않도록 BCrypt로 재인코딩한다.
 * 그 외 값은 이미 DelegatingPasswordEncoder 포맷({bcrypt}... 등)으로 간주해 그대로 저장한다.
 */
@Component
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
        String passwordHash = encodedPassword.startsWith(NOOP_PREFIX)
                ? passwordEncoder.encode(encodedPassword.substring(NOOP_PREFIX.length()))
                : encodedPassword;
        userAccountRepository.save(new UserAccount(username, passwordHash, role, displayName));
        if (!seeded.isEmpty()) {
            seeded.append(", ");
        }
        seeded.append(username).append("(").append(role).append(")");
    }
}
