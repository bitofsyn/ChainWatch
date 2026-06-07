package com.chainwatch.backend.collector.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration
@ConditionalOnProperty(prefix = "chainwatch.collector", name = "provider", havingValue = "rpc", matchIfMissing = true)
@ConditionalOnProperty(prefix = "chainwatch.ethereum", name = "rpc-url")
public class Web3jConfig {

    @Bean
    public Web3j web3j(EthereumProperties ethereumProperties) {
        return Web3j.build(new HttpService(ethereumProperties.rpcUrl()));
    }
}
