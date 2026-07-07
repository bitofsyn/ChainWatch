package com.chainwatch.backend.collector.polling;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chainwatch.backend.collector.config.CollectionMode;
import com.chainwatch.backend.collector.config.CollectorProperties;
import com.chainwatch.backend.collector.config.ProviderType;
import com.chainwatch.backend.collector.exception.CollectorException;
import com.chainwatch.backend.collector.metrics.CollectorMetrics;
import com.chainwatch.backend.collector.service.BlockCollectionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockPollingSchedulerTest {

    @Mock
    private BlockCollectionService blockCollectionService;

    private final CollectorMetrics metrics = new CollectorMetrics(new SimpleMeterRegistry());

    @Test
    void doesNothingWhenCollectorDisabled() {
        BlockPollingScheduler scheduler = scheduler(false, CollectionMode.POLLING);

        scheduler.poll();

        verifyNoInteractions(blockCollectionService);
    }

    @Test
    void doesNothingInWebsocketMode() {
        BlockPollingScheduler scheduler = scheduler(true, CollectionMode.WEBSOCKET);

        scheduler.poll();

        verifyNoInteractions(blockCollectionService);
    }

    @Test
    void collectsUpToLatestInPollingMode() {
        BlockPollingScheduler scheduler = scheduler(true, CollectionMode.POLLING);
        when(blockCollectionService.collectUpToLatest()).thenReturn(2);

        scheduler.poll();

        verify(blockCollectionService).collectUpToLatest();
    }

    @Test
    void survivesCollectorFailures() {
        BlockPollingScheduler scheduler = scheduler(true, CollectionMode.POLLING);
        when(blockCollectionService.collectUpToLatest()).thenThrow(new CollectorException("rpc down"));

        scheduler.poll();
    }

    private BlockPollingScheduler scheduler(boolean enabled, CollectionMode mode) {
        CollectorProperties properties = new CollectorProperties(
                ProviderType.RPC, mode, enabled, 15_000, 0, 5, 6, 12, null, null, null);
        return new BlockPollingScheduler(properties, blockCollectionService, metrics);
    }
}
