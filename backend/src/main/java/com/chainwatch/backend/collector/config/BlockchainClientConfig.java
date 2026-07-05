package com.chainwatch.backend.collector.config;

import com.chainwatch.backend.collector.client.BlockchainClient;
import com.chainwatch.backend.collector.client.EtherscanBlockchainClient;
import com.chainwatch.backend.collector.client.MeteredBlockchainClient;
import com.chainwatch.backend.collector.client.NoOpBlockchainClient;
import com.chainwatch.backend.collector.client.RetryingBlockchainClient;
import com.chainwatch.backend.collector.client.RpcBlockchainClient;
import com.chainwatch.backend.collector.client.ThrottledBlockchainClient;
import com.chainwatch.backend.collector.mapper.EthereumBlockMapper;
import com.chainwatch.backend.collector.metrics.CollectorMetrics;
import com.chainwatch.backend.collector.util.BackoffRetryExecutor;
import com.chainwatch.backend.collector.util.RequestThrottle;
import com.chainwatch.backend.collector.util.Sleeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.web3j.protocol.Web3j;

/**
 * BlockchainClient 조립 팩토리. 설정된 공급자(provider)에 맞는 구현체를 만들고
 * Metered → Throttled → Retrying 순으로 데코레이터를 감싼다.
 */
@Configuration
public class BlockchainClientConfig {

    private static final Logger log = LoggerFactory.getLogger(BlockchainClientConfig.class);

    @Bean
    public BackoffRetryExecutor collectorRetryExecutor(
            CollectorProperties collectorProperties,
            CollectorMetrics metrics
    ) {
        return new BackoffRetryExecutor(collectorProperties.retry(), Sleeper.THREAD_SLEEP, metrics::incrementRetry);
    }

    @Bean
    public BlockchainClient blockchainClient(
            CollectorProperties collectorProperties,
            EthereumProperties ethereumProperties,
            EtherscanProperties etherscanProperties,
            ObjectProvider<Web3j> web3jProvider,
            WebClient.Builder webClientBuilder,
            EthereumBlockMapper mapper,
            CollectorMetrics metrics,
            BackoffRetryExecutor collectorRetryExecutor
    ) {
        BlockchainClient client = createClient(
                collectorProperties, ethereumProperties, etherscanProperties,
                web3jProvider, webClientBuilder, mapper
        );
        if (client instanceof NoOpBlockchainClient) {
            return client;
        }

        RequestThrottle throttle = new RequestThrottle(collectorProperties.throttle().minIntervalMs());
        return new RetryingBlockchainClient(
                new ThrottledBlockchainClient(
                        new MeteredBlockchainClient(client, metrics),
                        throttle
                ),
                collectorRetryExecutor
        );
    }

    private BlockchainClient createClient(
            CollectorProperties collectorProperties,
            EthereumProperties ethereumProperties,
            EtherscanProperties etherscanProperties,
            ObjectProvider<Web3j> web3jProvider,
            WebClient.Builder webClientBuilder,
            EthereumBlockMapper mapper
    ) {
        String network = ethereumProperties.network();
        switch (collectorProperties.provider()) {
            case RPC -> {
                Web3j web3j = web3jProvider.getIfAvailable();
                if (web3j == null) {
                    log.warn("[COLLECTOR_START] provider=rpc but chainwatch.ethereum.http-url is not set");
                    return new NoOpBlockchainClient("chainwatch.ethereum.http-url is not set", network);
                }
                log.info("[COLLECTOR_START] provider=rpc network={}", network);
                return new RpcBlockchainClient(web3j, mapper, network);
            }
            case ETHERSCAN -> {
                if (etherscanProperties.apiKey() == null || etherscanProperties.apiKey().isBlank()) {
                    log.warn("[COLLECTOR_START] provider=etherscan but chainwatch.etherscan.api-key is not set");
                    return new NoOpBlockchainClient("chainwatch.etherscan.api-key is not set", network);
                }
                log.info("[COLLECTOR_START] provider=etherscan network={}", network);
                return new EtherscanBlockchainClient(webClientBuilder, etherscanProperties, network);
            }
            default -> {
                return new NoOpBlockchainClient("unsupported provider", network);
            }
        }
    }
}
