package com.chainwatch.backend.auth.exception;

/** 리프레시 토큰이 없거나 만료·폐기·재사용된 경우. 클라이언트는 재로그인해야 한다. */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh token is invalid or expired");
    }
}
