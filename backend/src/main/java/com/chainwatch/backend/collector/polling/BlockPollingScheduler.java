package com.chainwatch.backend.collector.polling;

import com.chainwatch.backend.collector.config.CollectionMode;
import com.chainwatch.backend.collector.config.CollectorProperties;
import com.chainwatch.backend.collector.metrics.CollectorMetrics;
import com.chainwatch.backend.collector.service.BlockCollectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 폴링 모드 수집기. WebSocket을 쓸 수 없는 환경에서 설정 주기로 최신 블록까지 따라잡는다.
 */
@Component
public class BlockPollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BlockPollingScheduler.class);

    private final CollectorProperties collectorProperties;
    private final BlockCollectionService blockCollectionService;
    private final CollectorMetrics metrics;

    public BlockPollingScheduler(
            CollectorProperties collectorProperties,
            BlockCollectionService blockCollectionService,
            CollectorMetrics metrics
    ) {
        this.collectorProperties = collectorProperties;
        this.blockCollectionService = blockCollectionService;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${chainwatch.collector.poll-interval-ms:15000}")
    public void poll() {
        if (!collectorProperties.enabled() || collectorProperties.mode() != CollectionMode.POLLING) {
            return;
        }
        try {
            int collected = blockCollectionService.collectUpToLatest();
            if (collected > 0) {
                log.debug("Polling cycle collected {} block(s)", collected);
            }
        } catch (RuntimeException exception) {
            // CollectorException 외에도 DB 제약 위반(멀티 인스턴스 중복 수집 경합) 등 런타임 예외를
            // 흡수해 에러 메트릭에 남기고, 다음 폴링 사이클이 저장된 상태에서 이어서 재시도하게 한다.
            metrics.incrementError();
            log.error("[ERROR] Polling cycle failed: {}", exception.getMessage(), exception);
        }
    }
}
