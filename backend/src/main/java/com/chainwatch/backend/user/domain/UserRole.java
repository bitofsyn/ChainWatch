package com.chainwatch.backend.user.domain;

/**
 * 운영 콘솔 사용자 역할. SecurityConfig의 URL 매처(hasRole)와 JWT roles claim이
 * 기대하는 ROLE_ 접두 문자열은 {@link #authority()}로 얻는다.
 */
public enum UserRole {
    ADMIN,
    ANALYST;

    public String authority() {
        return "ROLE_" + name();
    }
}
