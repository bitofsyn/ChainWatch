package com.chainwatch.backend.collector.exception;

/**
 * 수집기 설정 오류. 재시도해도 해결되지 않으므로 재시도 대상에서 제외된다.
 */
public class CollectorConfigurationException extends CollectorException {

    public CollectorConfigurationException(String message) {
        super(message);
    }
}
