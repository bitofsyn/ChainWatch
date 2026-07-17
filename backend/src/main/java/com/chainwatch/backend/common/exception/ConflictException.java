package com.chainwatch.backend.common.exception;

/** 요청이 현재 리소스 상태와 충돌하는 경우(중복 username, 마지막 활성 ADMIN 보호 등). HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
