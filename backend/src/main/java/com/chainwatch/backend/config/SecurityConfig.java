package com.chainwatch.backend.config;

import com.chainwatch.backend.security.JwtAuthenticationFilter;
import com.chainwatch.backend.security.RestAccessDeniedHandler;
import com.chainwatch.backend.security.RestAuthenticationEntryPoint;
import com.chainwatch.backend.security.SecurityProperties;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /** application.yml에 들어 있는 로컬 개발용 기본값. 공개 저장소에 노출된 값이므로 운영 사용을 차단한다. */
    private static final String DEFAULT_DEV_JWT_SECRET =
            "chainwatch-local-dev-secret-key-change-in-production-0123456789";
    private static final String DEFAULT_DEV_PASSWORD = "{noop}chainwatch";

    static final String ROLE_ADMIN = "ADMIN";
    static final String ROLE_ANALYST = "ANALYST";

    private static final String[] PUBLIC_PATHS = {
            "/api/auth/**",
            "/error",
            "/actuator/health",
            "/actuator/metrics/**",
            "/actuator/prometheus",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**"
    };

    /** 상태를 변경하는 운영자 제어 API. ADMIN 전용. */
    private static final String[] ADMIN_CONTROL_PATHS = {
            "/api/collector/blocks/**"
    };

    /** 감사 로그 열람은 ADMIN 전용 (감사 대상이 감사 기록을 조회하는 것을 막는다). */
    private static final String[] ADMIN_READ_PATHS = {
            "/api/audit-logs/**"
    };

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityProperties properties,
            Environment environment,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint restAuthenticationEntryPoint,
            RestAccessDeniedHandler restAccessDeniedHandler
    ) throws Exception {
        http.csrf(csrf -> csrf.disable());

        if (properties.jwtEnabled()) {
            if (DEFAULT_DEV_JWT_SECRET.equals(properties.jwtSecret())) {
                throw new IllegalStateException(
                        "chainwatch.security.jwt-secret is still the built-in development default. "
                                + "Set CHAINWATCH_JWT_SECRET to a strong random value "
                                + "(e.g. `openssl rand -hex 32`) before enabling JWT security.");
            }
            if (DEFAULT_DEV_PASSWORD.equals(properties.adminPassword())) {
                log.warn("chainwatch.security.admin-password is still the built-in development default. "
                        + "Set CHAINWATCH_ADMIN_PASSWORD to an encoded strong password for production use.");
            }
            if (DEFAULT_DEV_PASSWORD.equals(properties.analystPassword())) {
                log.warn("chainwatch.security.analyst-password is still the built-in development default. "
                        + "Set CHAINWATCH_ANALYST_PASSWORD to an encoded strong password for production use.");
            }
            http
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(PUBLIC_PATHS).permitAll()
                            .requestMatchers(HttpMethod.POST, ADMIN_CONTROL_PATHS).hasRole(ROLE_ADMIN)
                            .requestMatchers(ADMIN_READ_PATHS).hasRole(ROLE_ADMIN)
                            .requestMatchers("/api/**").hasAnyRole(ROLE_ADMIN, ROLE_ANALYST)
                            .anyRequest().authenticated()
                    )
                    .exceptionHandling(handling -> handling
                            .authenticationEntryPoint(restAuthenticationEntryPoint)
                            .accessDeniedHandler(restAccessDeniedHandler))
                    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        } else {
            rejectJwtDisabledOnProductionLikeProfile(environment.getActiveProfiles());
            log.warn("JWT security is DISABLED (chainwatch.security.jwt-enabled=false): "
                    + "all /api endpoints are publicly accessible. Do not use this mode in production.");
            http
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(PUBLIC_PATHS).permitAll()
                            .requestMatchers("/api/**").permitAll()
                            .anyRequest().authenticated()
                    )
                    .httpBasic(Customizer.withDefaults());
        }

        return http.build();
    }

    /**
     * 운영성 프로필(prod, production, prod-*, staging)에서 JWT 비활성 기동을 fail-fast로 차단한다.
     * jwt-enabled=false는 로컬 개발 편의를 위한 모드이며 운영 환경에서는 전체 API가 무인증 노출된다.
     */
    static void rejectJwtDisabledOnProductionLikeProfile(String[] activeProfiles) {
        for (String profile : activeProfiles) {
            String normalized = profile.toLowerCase(Locale.ROOT);
            if (normalized.contains("prod") || normalized.equals("staging")) {
                throw new IllegalStateException(
                        "chainwatch.security.jwt-enabled=false is not allowed with production-like profile '"
                                + profile + "': every /api endpoint would be publicly accessible. "
                                + "Set CHAINWATCH_JWT_ENABLED=true and configure CHAINWATCH_JWT_SECRET, "
                                + "CHAINWATCH_ADMIN_PASSWORD, CHAINWATCH_ANALYST_PASSWORD before starting.");
            }
        }
    }
}
