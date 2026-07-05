package com.chainwatch.backend.collector.websocket;

import com.chainwatch.backend.collector.config.EthereumProperties;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.websocket.WebSocketService;

@Component
public class DefaultWeb3jWebSocketConnector implements Web3jWebSocketConnector {

    private final EthereumProperties ethereumProperties;

    public DefaultWeb3jWebSocketConnector(EthereumProperties ethereumProperties) {
        this.ethereumProperties = ethereumProperties;
    }

    @Override
    public WebSocketConnection connect() throws IOException {
        WebSocketService webSocketService = new WebSocketService(ethereumProperties.wsUrl(), false);
        webSocketService.connect();
        return new WebSocketConnection(Web3j.build(webSocketService), webSocketService::close);
    }
}
