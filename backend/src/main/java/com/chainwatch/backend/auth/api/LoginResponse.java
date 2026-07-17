package com.chainwatch.backend.auth.api;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        String refreshToken,
        UserSummary user
) {
    private static final String TOKEN_TYPE_BEARER = "Bearer";

    public static LoginResponse bearer(
            String accessToken, long expiresInSeconds, String refreshToken, UserSummary user) {
        return new LoginResponse(accessToken, TOKEN_TYPE_BEARER, expiresInSeconds, refreshToken, user);
    }
}
