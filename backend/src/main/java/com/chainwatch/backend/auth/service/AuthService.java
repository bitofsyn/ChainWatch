package com.chainwatch.backend.auth.service;

import com.chainwatch.backend.audit.service.AuditLogService;
import com.chainwatch.backend.auth.api.LoginRequest;
import com.chainwatch.backend.auth.api.LoginResponse;
import com.chainwatch.backend.auth.exception.InvalidCredentialsException;
import com.chainwatch.backend.security.JwtTokenProvider;
import com.chainwatch.backend.security.SecurityProperties;
import java.util.List;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_ANALYST = "ROLE_ANALYST";

    private static final String AUDIT_ACTION_LOGIN_SUCCESS = "LOGIN_SUCCESS";
    private static final String AUDIT_ACTION_LOGIN_FAILURE = "LOGIN_FAILURE";
    private static final String AUDIT_TARGET_TYPE_AUTH = "AUTH";

    private final SecurityProperties properties;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public AuthService(
            SecurityProperties properties,
            JwtTokenProvider jwtTokenProvider,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService
    ) {
        this.properties = properties;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    public LoginResponse login(LoginRequest request) {
        Optional<String> role = resolveRole(request);
        if (role.isEmpty()) {
            auditLogService.record(request.username(), null, AUDIT_ACTION_LOGIN_FAILURE,
                    AUDIT_TARGET_TYPE_AUTH, request.username(), "Invalid username or password");
            throw new InvalidCredentialsException();
        }
        String token = jwtTokenProvider.createToken(request.username(), List.of(role.get()));
        auditLogService.record(request.username(), role.get(), AUDIT_ACTION_LOGIN_SUCCESS,
                AUDIT_TARGET_TYPE_AUTH, request.username(), "Login succeeded");
        return LoginResponse.bearer(token, jwtTokenProvider.expirationSeconds());
    }

    private Optional<String> resolveRole(LoginRequest request) {
        if (matches(properties.adminUsername(), properties.adminPassword(), request)) {
            return Optional.of(ROLE_ADMIN);
        }
        if (matches(properties.analystUsername(), properties.analystPassword(), request)) {
            return Optional.of(ROLE_ANALYST);
        }
        return Optional.empty();
    }

    private boolean matches(String username, String encodedPassword, LoginRequest request) {
        if (username == null || username.isBlank() || encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }
        return username.equals(request.username())
                && passwordEncoder.matches(request.password(), encodedPassword);
    }
}
