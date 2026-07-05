package com.chainwatch.backend.config;

import com.chainwatch.backend.security.JwtAuthenticationFilter;
import com.chainwatch.backend.security.RestAuthenticationEntryPoint;
import com.chainwatch.backend.security.SecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
    private static final String DEFAULT_DEV_ADMIN_PASSWORD = "{noop}chainwatch";

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

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityProperties properties,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint restAuthenticationEntryPoint
    ) throws Exception {
        http.csrf(csrf -> csrf.disable());

        if (properties.jwtEnabled()) {
            if (DEFAULT_DEV_JWT_SECRET.equals(properties.jwtSecret())) {
                throw new IllegalStateException(
                        "chainwatch.security.jwt-secret is still the built-in development default. "
                                + "Set CHAINWATCH_JWT_SECRET to a strong random value "
                                + "(e.g. `openssl rand -hex 32`) before enabling JWT security.");
            }
            if (DEFAULT_DEV_ADMIN_PASSWORD.equals(properties.adminPassword())) {
                log.warn("chainwatch.security.admin-password is still the built-in development default. "
                        + "Set CHAINWATCH_ADMIN_PASSWORD to an encoded strong password for production use.");
            }
            http
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(PUBLIC_PATHS).permitAll()
                            .requestMatchers("/api/**").authenticated()
                            .anyRequest().authenticated()
                    )
                    .exceptionHandling(handling -> handling.authenticationEntryPoint(restAuthenticationEntryPoint))
                    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        } else {
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
}
