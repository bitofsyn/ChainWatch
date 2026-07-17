package com.chainwatch.backend.user.api;

/** 생성 직후 1회만 초기 비밀번호를 노출한다(서버 생성 시). 이후 어떤 API로도 재조회 불가. */
public record UserCreateResponse(
        UserResponse user,
        String initialPassword
) {
}
