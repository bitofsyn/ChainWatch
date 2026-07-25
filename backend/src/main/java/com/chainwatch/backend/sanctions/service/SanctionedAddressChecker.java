package com.chainwatch.backend.sanctions.service;

/**
 * 탐지 룰이 제재 주소 여부를 조회하는 인터페이스.
 * 룰은 이 인터페이스로만 조회해, 동기화로 갱신된 스냅샷이 재기동 없이 즉시 반영된다.
 */
public interface SanctionedAddressChecker {

    /** 입력 주소는 원문 그대로 받고 내부에서 소문자 정규화해 비교한다. */
    boolean isSanctioned(String address);
}
