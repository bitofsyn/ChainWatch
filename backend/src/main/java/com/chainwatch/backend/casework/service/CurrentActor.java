package com.chainwatch.backend.casework.service;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 업무 기록에 남길 현재 사용자 이름. 인증이 없으면(JWT 비활성 개발 모드) "anonymous". */
public final class CurrentActor {

    private CurrentActor() {
    }

    public static String name() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || authentication.getName() == null || authentication.getName().isBlank()) {
            return "anonymous";
        }
        return authentication.getName();
    }
}
