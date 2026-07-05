package com.chainwatch.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final String CLAIM_ROLES = "roles";

    private final SecretKey secretKey;
    private final Duration expiration;

    public JwtTokenProvider(SecurityProperties properties) {
        this.secretKey = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofMinutes(properties.jwtExpirationMinutes());
    }

    public String createToken(String username, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_ROLES, roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(secretKey)
                .compact();
    }

    public long expirationSeconds() {
        return expiration.toSeconds();
    }

    public Optional<TokenPayload> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            List<?> rawRoles = claims.get(CLAIM_ROLES, List.class);
            List<String> roles = rawRoles == null
                    ? List.of()
                    : rawRoles.stream().map(String::valueOf).toList();
            return Optional.of(new TokenPayload(claims.getSubject(), roles));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public record TokenPayload(String username, List<String> roles) {
    }
}
