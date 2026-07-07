package com.chainwatch.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-for-jwt-token-provider-unit-tests-0123456789";

    private JwtTokenProvider provider(long expirationMinutes) {
        SecurityProperties properties = new SecurityProperties(
                true, SECRET, expirationMinutes, "admin", "{noop}password", "analyst", "{noop}password");
        return new JwtTokenProvider(properties);
    }

    @Test
    void createAndParseRoundTrip() {
        JwtTokenProvider provider = provider(60);

        String token = provider.createToken("admin", List.of("ROLE_ADMIN"));
        Optional<JwtTokenProvider.TokenPayload> payload = provider.parse(token);

        assertThat(payload).isPresent();
        assertThat(payload.get().username()).isEqualTo("admin");
        assertThat(payload.get().roles()).containsExactly("ROLE_ADMIN");
    }

    @Test
    void parseRejectsMalformedToken() {
        JwtTokenProvider provider = provider(60);

        assertThat(provider.parse("not-a-jwt")).isEmpty();
    }

    @Test
    void parseRejectsTokenSignedWithDifferentSecret() {
        JwtTokenProvider provider = provider(60);
        JwtTokenProvider otherProvider = new JwtTokenProvider(new SecurityProperties(
                true, "another-secret-key-that-is-long-enough-for-hmac-sha-0123456789",
                60, "admin", "{noop}password", "analyst", "{noop}password"));

        String token = otherProvider.createToken("admin", List.of("ROLE_ADMIN"));

        assertThat(provider.parse(token)).isEmpty();
    }

    @Test
    void parseRejectsExpiredToken() {
        JwtTokenProvider provider = provider(0);

        String token = provider.createToken("admin", List.of("ROLE_ADMIN"));

        assertThat(provider.parse(token)).isEmpty();
    }
}
