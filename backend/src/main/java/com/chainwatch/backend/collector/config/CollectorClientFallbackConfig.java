package com.chainwatch.backend.collector.config;

import com.chainwatch.backend.collector.client.BlockClient;
import com.chainwatch.backend.collector.client.CollectedBlock;
import com.chainwatch.backend.collector.exception.CollectorException;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CollectorClientFallbackConfig {

    private static final String MESSAGE =
            "No collector source is configured. Set chainwatch.collector.provider and the corresponding credentials.";

    @Bean
    @ConditionalOnMissingBean(BlockClient.class)
    public BlockClient noOpBlockClient() {
        return new BlockClient() {
            @Override
            public long getLatestBlockNumber() throws IOException {
                throw new CollectorException(MESSAGE);
            }

            @Override
            public CollectedBlock getBlock(long blockNumber) throws IOException {
                throw new CollectorException(MESSAGE);
            }
        };
    }
}
