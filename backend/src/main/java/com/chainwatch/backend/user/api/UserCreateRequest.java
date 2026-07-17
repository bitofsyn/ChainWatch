package com.chainwatch.backend.user.api;

import com.chainwatch.backend.user.domain.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
        @NotBlank @Size(min = 3, max = 50)
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "영문/숫자/._- 만 사용할 수 있습니다")
        String username,
        @NotNull UserRole role,
        @Size(max = 100) String displayName,
        /** 미지정 시 서버가 임시 비밀번호를 생성해 응답에 1회 포함한다. */
        @Size(min = 8, max = 100) String initialPassword
) {
}
