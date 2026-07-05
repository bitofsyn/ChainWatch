package com.chainwatch.backend.collector.exception;

/**
 * RPC 호출의 일시적 실패(타임아웃, 연결 끊김, rate limit 등). 재시도 대상이다.
 */
public class RpcClientException extends CollectorException {

    public RpcClientException(String message) {
        super(message);
    }

    public RpcClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
