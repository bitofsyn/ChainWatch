package com.chainwatch.backend.auth.api;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {
    private static final String TOKEN_TYPE_BEARER = "Bearer";

    public static LoginResponse bearer(String accessToken, long expiresInSeconds) {
        return new LoginResponse(accessToken, TOKEN_TYPE_BEARER, expiresInSeconds);
    }
}
