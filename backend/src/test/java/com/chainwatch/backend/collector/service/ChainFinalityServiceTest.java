package com.chainwatch.backend.collector.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.chainwatch.backend.collector.config.CollectionMode;
import com.chainwatch.backend.collector.config.CollectorProperties;
import com.chainwatch.backend.collector.config.ProviderType;
import com.chainwatch.backend.collector.domain.CollectorState;
import com.chainwatch.backend.collector.repository.CollectorStateRepository;
import com.chainwatch.backend.collector.service.ChainFinalityService.Confirmation;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChainFinalityServiceTest {

    private static final int CONFIRMATION_DEPTH = 12;

    @Mock
    private CollectorStateRepository collectorStateRepository;

    private ChainFinalityService service;

    @BeforeEach
    void setUp() {
        CollectorProperties properties = new CollectorProperties(
                ProviderType.RPC, CollectionMode.POLLING, true,
                15_000, 0, 5, 6, CONFIRMATION_DEPTH, null, null, null);
        service = new ChainFinalityService(collectorStateRepository, properties);
    }

    @Test
    void defaultsConfirmationDepthDeeperThanReorgRewind() {
        CollectorProperties defaults = new CollectorProperties(
                null, null, false, 0, 0, 0, 0, 0, null, null, null);
        assertThat(defaults.confirmationDepth()).isEqualTo(12);
        assertThat(defaults.confirmationDepth()).isGreaterThan(defaults.reorgRewindDepth());
    }

    @Test
    void headIsEmptyWhenCollectorNeverRan() {
        when(collectorStateRepository.findById(any())).thenReturn(Optional.empty());

        assertThat(service.lastKnownChainHead()).isEmpty();
    }

    @Test
    void headIsEmptyWhenLegacyStateHasNoObservedHead() {
        when(collectorStateRepository.findById(any()))
                .thenReturn(Optional.of(new CollectorState("ethereum-main-collector", 100L, Instant.now())));

        assertThat(service.lastKnownChainHead()).isEmpty();
    }

    @Test
    void returnsUnknownConfirmationWhenHeadUnknown() {
        Confirmation confirmation = service.confirmationFor(100L, null);

        assertThat(confirmation.confirmations()).isNull();
        assertThat(confirmation.confirmed()).isNull();
    }

    @Test
    void blockAtExactDepthBoundaryIsConfirmed() {
        // head=111, block=100 → confirmations = 12 = depth → 확정
        Confirmation confirmation = service.confirmationFor(100L, 111L);

        assertThat(confirmation.confirmations()).isEqualTo(12L);
        assertThat(confirmation.confirmed()).isTrue();
    }

    @Test
    void blockWithinDepthWindowIsUnconfirmed() {
        // head=110, block=100 → confirmations = 11 < 12 → 미확정 (reorg rewind 도달 가능 구간)
        Confirmation confirmation = service.confirmationFor(100L, 110L);

        assertThat(confirmation.confirmations()).isEqualTo(11L);
        assertThat(confirmation.confirmed()).isFalse();
    }

    @Test
    void blockAheadOfStaleHeadIsTreatedAsZeroConfirmations() {
        Confirmation confirmation = service.confirmationFor(200L, 100L);

        assertThat(confirmation.confirmations()).isZero();
        assertThat(confirmation.confirmed()).isFalse();
    }
}
