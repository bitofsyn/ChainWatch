package com.chainwatch.backend.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * jwt-enabled=false는 로컬 개발 전용 모드다. 운영성 프로필로 기동하면
 * 애플리케이션이 뜨기 전에 fail-fast로 거부해야 한다.
 */
class SecurityConfigProdGuardTest {

    @Test
    void prodProfileWithJwtDisabledFailsFast() {
        assertThatThrownBy(() -> SecurityConfig.rejectJwtDisabledOnProductionLikeProfile(new String[]{"prod"}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt-enabled=false")
                .hasMessageContaining("prod");
    }

    @Test
    void productionAndStagingVariantsAreRejected() {
        for (String profile : new String[]{"production", "PROD", "prod-eu", "staging"}) {
            assertThatThrownBy(() -> SecurityConfig.rejectJwtDisabledOnProductionLikeProfile(new String[]{profile}))
                    .as("profile %s must be rejected", profile)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void localAndDefaultProfilesAreAllowed() {
        assertThatCode(() -> SecurityConfig.rejectJwtDisabledOnProductionLikeProfile(new String[]{}))
                .doesNotThrowAnyException();
        assertThatCode(() -> SecurityConfig.rejectJwtDisabledOnProductionLikeProfile(new String[]{"local", "dev", "test"}))
                .doesNotThrowAnyException();
    }
}
