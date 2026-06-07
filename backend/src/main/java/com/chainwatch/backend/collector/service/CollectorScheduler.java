package com.chainwatch.backend.collector.service;

import com.chainwatch.backend.collector.config.CollectorProperties;
import com.chainwatch.backend.collector.exception.CollectorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CollectorScheduler {

    private static final Logger log = LoggerFactory.getLogger(CollectorScheduler.class);

    private final CollectorProperties collectorProperties;
    private final CollectorService collectorService;

    public CollectorScheduler(CollectorProperties collectorProperties, CollectorService collectorService) {
        this.collectorProperties = collectorProperties;
        this.collectorService = collectorService;
    }

    @Scheduled(fixedDelayString = "${chainwatch.collector.fixed-delay-ms:15000}")
    public void collectLatestBlock() {
        if (!collectorProperties.enabled()) {
            return;
        }

        try {
            collectorService.collectNextBlock();
        } catch (CollectorException exception) {
            log.error("Collector scheduler failed: {}", exception.getMessage(), exception);
        }
    }
}
