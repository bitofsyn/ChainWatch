package com.chainwatch.backend.auth.service;

import com.chainwatch.backend.auth.api.LoginRequest;
import com.chainwatch.backend.auth.api.LoginResponse;
import com.chainwatch.backend.auth.exception.InvalidCredentialsException;
import com.chainwatch.backend.security.JwtTokenProvider;
import com.chainwatch.backend.security.SecurityProperties;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final SecurityProperties properties;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    public AuthService(SecurityProperties properties, JwtTokenProvider jwtTokenProvider, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(LoginRequest request) {
        boolean usernameMatches = properties.adminUsername().equals(request.username());
        boolean passwordMatches = passwordEncoder.matches(request.password(), properties.adminPassword());
        if (!usernameMatches || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        String token = jwtTokenProvider.createToken(request.username(), List.of(ROLE_ADMIN));
        return LoginResponse.bearer(token, jwtTokenProvider.expirationSeconds());
    }
}
