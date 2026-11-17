package com.chainwatch.backend.auth.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로그인 화면의 "둘러보기" 버튼용 데모 계정. 계정이 없는 방문자가 비밀번호 없이
 * 조회자 또는 분석가 화면을 체험할 수 있게 한다. 관리자 데모는 제공하지 않는다.
 */
@ConfigurationProperties(prefix = "chainwatch.demo")
public record DemoLoginProperties(boolean loginEnabled) {
}
