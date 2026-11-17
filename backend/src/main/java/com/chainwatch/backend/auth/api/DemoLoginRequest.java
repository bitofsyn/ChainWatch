package com.chainwatch.backend.auth.api;

import jakarta.validation.constraints.NotBlank;

/** 둘러보기 로그인 요청. role은 VIEWER 또는 ANALYST. */
public record DemoLoginRequest(@NotBlank String role) {
}
