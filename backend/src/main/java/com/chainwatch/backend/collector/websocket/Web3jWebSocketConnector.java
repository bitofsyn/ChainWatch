package com.chainwatch.backend.collector.websocket;

import java.io.IOException;
import org.web3j.protocol.Web3j;

/**
 * WebSocket 연결 생성 추상화. 테스트에서 실제 네트워크 없이 재연결 로직을 검증할 수 있게 한다.
 */
public interface Web3jWebSocketConnector {

    WebSocketConnection connect() throws IOException;

    record WebSocketConnection(Web3j web3j, Runnable closer) {

        public void close() {
            closer.run();
        }
    }
}
