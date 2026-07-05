package com.chainwatch.backend.collector.config;

import java.time.Duration;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

/**
 * Web3j HTTP RPC 연결 설정. WebSocket 연결은 재연결 수명주기 관리가 필요하므로
 * EthereumWebSocketSubscriber가 직접 소유한다.
 */
@Configuration
@ConditionalOnProperty(prefix = "chainwatch.collector", name = "provider", havingValue = "rpc", matchIfMissing = true)
@ConditionalOnProperty(prefix = "chainwatch.ethereum", name = "http-url")
public class Web3jConfig {

    private static final Logger log = LoggerFactory.getLogger(Web3jConfig.class);

    @Bean(destroyMethod = "shutdown")
    public Web3j web3j(EthereumProperties ethereumProperties) {
        Duration timeout = Duration.ofSeconds(ethereumProperties.requestTimeoutSeconds());
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(timeout)
                .callTimeout(timeout.plusSeconds(5))
                .build();

        log.info("[RPC_CONNECTED] Web3j HTTP client initialized (network={})", ethereumProperties.network());
        return Web3j.build(new HttpService(ethereumProperties.httpUrl(), httpClient));
    }
}
