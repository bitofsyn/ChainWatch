package com.chainwatch.backend.collector.util;

/**
 * 대기 동작 추상화. 테스트에서 실제 sleep 없이 지연을 검증할 수 있게 한다.
 */
@FunctionalInterface
public interface Sleeper {

    Sleeper THREAD_SLEEP = Thread::sleep;

    void sleep(long millis) throws InterruptedException;
}
