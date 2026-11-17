package com.chainwatch.backend.auth.demo;

import com.chainwatch.backend.user.domain.UserAccount;
import com.chainwatch.backend.user.domain.UserRole;
import com.chainwatch.backend.user.repository.UserAccountRepository;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 둘러보기용 데모 계정을 준비한다. 비밀번호는 아무도 모르는 임의 값이라 일반 로그인으로는
 * 들어올 수 없고, POST /api/auth/demo로만 세션이 발급된다.
 *
 * 초기 계정 시딩(UserBootstrapRunner)은 user_accounts가 비어 있을 때만 동작하므로
 * 반드시 그 뒤에 실행되어야 한다.
 */
@Component
@Order(20)
public class DemoAccountRunner implements ApplicationRunner {

    public static final String VIEWER_USERNAME = "demo-viewer";
    public static final String ANALYST_USERNAME = "demo-analyst";

    private static final Logger log = LoggerFactory.getLogger(DemoAccountRunner.class);

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final DemoLoginProperties properties;

    public DemoAccountRunner(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            DemoLoginProperties properties
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        sync(VIEWER_USERNAME, UserRole.VIEWER, "둘러보기 조회자");
        sync(ANALYST_USERNAME, UserRole.ANALYST, "둘러보기 분석가");
    }

    private void sync(String username, UserRole role, String displayName) {
        Optional<UserAccount> existing = userAccountRepository.findByUsername(username);
        if (!properties.loginEnabled()) {
            // 데모를 끄면 남아 있는 데모 계정도 비활성화한다.
            existing.filter(UserAccount::isActive).ifPresent(account -> {
                account.deactivate();
                userAccountRepository.save(account);
            });
            return;
        }
        if (existing.isPresent()) {
            UserAccount account = existing.get();
            if (!account.isActive()) {
                account.activate();
                userAccountRepository.save(account);
            }
            return;
        }
        String unusablePassword = passwordEncoder.encode(UUID.randomUUID().toString());
        userAccountRepository.save(new UserAccount(username, unusablePassword, role, displayName));
        log.info("[DEMO_LOGIN] created demo account '{}' ({})", username, role);
    }
}
