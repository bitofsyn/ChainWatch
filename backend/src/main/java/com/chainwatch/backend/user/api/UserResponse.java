package com.chainwatch.backend.user.api;

import com.chainwatch.backend.user.domain.UserAccount;
import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        String role,
        String displayName,
        boolean active,
        Instant createdAt,
        Instant lastLoginAt
) {
    public static UserResponse from(UserAccount user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                user.getDisplayName(),
                user.isActive(),
                user.getCreatedAt(),
                user.getLastLoginAt()
        );
    }
}
