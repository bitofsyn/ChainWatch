package com.chainwatch.backend.sanctions.exception;

/** 제재 주소 리스트 동기화 실패(다운로드 실패, 빈/비정상 리스트). 기존 스냅샷은 유지된다. */
public class SanctionsSyncException extends RuntimeException {

    public SanctionsSyncException(String message) {
        super(message);
    }

    public SanctionsSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
