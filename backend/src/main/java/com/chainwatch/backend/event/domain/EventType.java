package com.chainwatch.backend.event.domain;

public enum EventType {
    LARGE_TRANSFER,
    WHALE_ACTIVITY,
    EXCHANGE_FLOW,
    RAPID_TRANSFER,
    WATCHLIST_MATCH,
    /** 자금 분산(peeling chain/스플리팅) 그래프 패턴: 한 지갑이 짧은 시간에 다수의 서로 다른 주소로 송금. */
    FAN_OUT
}
