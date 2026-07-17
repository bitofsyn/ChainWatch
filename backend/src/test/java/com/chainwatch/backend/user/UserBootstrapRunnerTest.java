package com.chainwatch.backend.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.chainwatch.backend.user.bootstrap.UserBootstrapRunner;
import com.chainwatch.backend.user.domain.UserAccount;
import com.chainwatch.backend.user.domain.UserRole;
import com.chainwatch.backend.user.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;

/** 기동 시딩: 빈 테이블에서 admin/analyst 생성, 재실행 멱등, {noop} 비밀번호 BCrypt 재인코딩. */
@SpringBootTest(properties = {
        // 컨텍스트 캐시 분리용 고유 속성(다른 테스트의 사용자 생성과 격리)
        "chainwatch.security.refresh-expiration-days=16"
})
class UserBootstrapRunnerTest {

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserBootstrapRunner runner;

    @Test
    void seedsAdminAndAnalystFromPropertiesOnEmptyTable() {
        UserAccount admin = userAccountRepository.findByUsername("admin").orElseThrow();
        UserAccount analyst = userAccountRepository.findByUsername("analyst").orElseThrow();

        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.isActive()).isTrue();
        assertThat(analyst.getRole()).isEqualTo(UserRole.ANALYST);
        // {noop}chainwatch 개발 기본값은 평문 마커로 저장하지 않고 BCrypt로 재인코딩한다
        assertThat(admin.getPasswordHash()).startsWith("{bcrypt}");
        assertThat(analyst.getPasswordHash()).startsWith("{bcrypt}");
    }

    @Test
    void rerunOnNonEmptyTableSeedsNothing() {
        long before = userAccountRepository.count();

        runner.run(new DefaultApplicationArguments());

        assertThat(userAccountRepository.count()).isEqualTo(before);
    }
}
