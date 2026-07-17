package com.chainwatch.backend.user.api;

import com.chainwatch.backend.user.domain.UserRole;
import jakarta.validation.constraints.Size;

/** 부분 수정: null 필드는 변경하지 않는다. */
public record UserUpdateRequest(
        UserRole role,
        @Size(max = 100) String displayName,
        Boolean active
) {
}
