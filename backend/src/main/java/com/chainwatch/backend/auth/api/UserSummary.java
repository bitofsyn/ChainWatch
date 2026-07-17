package com.chainwatch.backend.auth.api;

import com.chainwatch.backend.user.domain.UserAccount;

/** 로그인/조회 응답에 실어 보내는 사용자 요약. role은 ROLE_ 접두 없는 enum 이름(ADMIN/ANALYST). */
public record UserSummary(
        String username,
        String role,
        String displayName
) {
    public static UserSummary from(UserAccount user) {
        return new UserSummary(user.getUsername(), user.getRole().name(), user.getDisplayName());
    }
}
