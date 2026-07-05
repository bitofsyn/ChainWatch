package com.chainwatch.backend.config;

import com.chainwatch.backend.security.JwtAuthenticationFilter;
import com.chainwatch.backend.security.RestAuthenticationEntryPoint;
import com.chainwatch.backend.security.SecurityProperties;
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

    private static final String[] PUBLIC_PATHS = {
            "/api/auth/**",
            "/actuator/health",
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
