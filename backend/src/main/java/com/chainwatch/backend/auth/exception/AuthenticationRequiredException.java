package com.chainwatch.backend.auth.exception;

/**
 * 인증 컨텍스트가 필요한 엔드포인트(/me, 비밀번호 변경 등)에 익명으로 접근한 경우.
 * jwt-enabled=false 모드에서는 시큐리티 체인이 막아주지 않으므로 컨트롤러 레벨에서 사용한다.
 */
public class AuthenticationRequiredException extends RuntimeException {

    public AuthenticationRequiredException() {
        super("Authentication is required");
    }
}
