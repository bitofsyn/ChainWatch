package com.chainwatch.backend.user.api;

import jakarta.validation.constraints.Size;

/** ADMIN의 비밀번호 초기화. newPassword 미지정 시 서버가 생성해 응답에 1회 포함한다. */
public record PasswordResetRequest(
        @Size(min = 8, max = 100) String newPassword
) {
}
