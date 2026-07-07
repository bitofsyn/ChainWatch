package com.chainwatch.backend.collector.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainwatch.backend.collector.api.CollectorResponse;
import com.chainwatch.backend.collector.client.BlockchainClient;
import com.chainwatch.backend.collector.config.CollectionMode;
import com.chainwatch.backend.collector.config.CollectorProperties;
import com.chainwatch.backend.collector.config.ProviderType;
import com.chainwatch.backend.collector.domain.CollectorState;
import com.chainwatch.backend.collector.dto.BlockDto;
import com.chainwatch.backend.collector.kafka.RawEventPublisher;
import com.chainwatch.backend.collector.metrics.CollectorMetrics;
import com.chainwatch.backend.collector.repository.CollectorStateRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockCollectionServiceTest {

    private static final String NETWORK = "ethereum-mainnet";

    @Mock
    private BlockchainClient blockchainClient;

    @Mock
    private CollectorStateRepository collectorStateRepository;

    @Mock
    private CollectedBlockProcessor blockProcessor;

    @Mock
    private RawEventPublisher rawEventPublisher;

    private BlockCollectionService service;

    @BeforeEach
    void setUp() {
        CollectorProperties properties = new CollectorProperties(
                ProviderType.RPC, CollectionMode.POLLING, true,
                15_000, 0, 5, 6, 12, null, null, null);
        service = new BlockCollectionService(
                blockchainClient, properties, collectorStateRepository,
                blockProcessor, rawEventPublisher,
                new CollectorMetrics(new SimpleMeterRegistry()));
    }

    @Test
    void startsFromChainHeadWhenNoStateAndNoStartBlock() {
        when(blockchainClient.fetchLatestBlockNumber()).thenReturn(200L);
        when(collectorStateRepository.findById(any())).thenReturn(Optional.empty());
        when(blockchainClient.fetchBlock(200L)).thenReturn(block(200));
        when(blockProcessor.process(any())).thenReturn(List.of());

        int collected = service.collectUpToLatest();

        assertThat(collected).isEqualTo(1);
        verify(blockchainClient).fetchBlock(200L);
        verify(rawEventPublisher).publishBlock(any(BlockDto.class));
    }

    @Test
    void resumesFromLastCollectedBlockAfterRestart() {
        when(blockchainClient.fetchLatestBlockNumber()).thenReturn(12L);
        when(collectorStateRepository.findById(any()))
                .thenReturn(Optional.of(new CollectorState("ethereum-main-collector", 9L, Instant.now())));
        when(blockchainClient.fetchBlock(anyLong())).thenAnswer(invocation -> block(invocation.getArgument(0)));
        when(blockProcessor.process(any())).thenReturn(List.of());

        int collected = service.collectUpToLatest();

        assertThat(collected).isEqualTo(3);
        verify(blockchainClient).fetchBlock(10L);
        verify(blockchainClient).fetchBlock(11L);
        verify(blockchainClient).fetchBlock(12L);
        verify(rawEventPublisher, times(3)).publishBlock(any(BlockDto.class));
    }

    @Test
    void limitsCatchUpBatchToMaxBlocksPerPoll() {
        when(blockchainClient.fetchLatestBlockNumber()).thenReturn(100L);
        when(collectorStateRepository.findById(any()))
                .thenReturn(Optional.of(new CollectorState("ethereum-main-collector", 0L, Instant.now())));
        when(blockchainClient.fetchBlock(anyLong())).thenAnswer(invocation -> block(invocation.getArgument(0)));
        when(blockProcessor.process(any())).thenReturn(List.of());

        int collected = service.collectUpToLatest();

        assertThat(collected).isEqualTo(5);
        verify(blockchainClient).fetchBlock(1L);
        verify(blockchainClient).fetchBlock(5L);
        verify(blockchainClient, never()).fetchBlock(6L);
    }

    @Test
    void skipsWhenTargetAlreadyCollected() {
        when(collectorStateRepository.findById(any()))
                .thenReturn(Optional.of(new CollectorState("ethereum-main-collector", 50L, Instant.now())));

        int collected = service.collectUpTo(50L);

        assertThat(collected).isZero();
        verify(blockchainClient, never()).fetchBlock(anyLong());
    }

    @Test
    void manualSingleBlockCollectionReturnsResponse() {
        when(blockchainClient.fetchBlock(42L)).thenReturn(block(42));
        when(blockProcessor.process(any())).thenReturn(List.of());

        CollectorResponse response = service.collectBlock(42L);

        assertThat(response.blockNumber()).isEqualTo(42L);
        assertThat(response.network()).isEqualTo(NETWORK);
        assertThat(response.provider()).isEqualTo("RPC");
        verify(rawEventPublisher).publishBlock(any(BlockDto.class));
    }

    @Test
    void rewindsStateWhenReorgDetected() {
        CollectorState state = new CollectorState("ethereum-main-collector", 99L, Instant.now());
        state.updateLastCollectedBlock(99L, "0xcanonical99");
        when(collectorStateRepository.findById(any())).thenReturn(Optional.of(state));
        BlockDto reorgedBlock = new BlockDto(100L, "0xhash100", "0xuncle99", Instant.EPOCH, "0xminer",
                BigInteger.ZERO, BigInteger.ZERO, NETWORK, List.of());
        when(blockchainClient.fetchBlock(100L)).thenReturn(reorgedBlock);

        int collected = service.collectUpTo(101L);

        assertThat(collected).isZero();
        assertThat(state.getLastCollectedBlock()).isEqualTo(93L); // 100 - 1 - rewindDepth(6)
        verify(collectorStateRepository, atLeastOnce()).save(state);
        verify(blockProcessor, never()).process(any());
        verify(blockchainClient, never()).fetchBlock(101L);
    }

    @Test
    void recordsObservedChainHeadWhenCollecting() {
        CollectorState state = new CollectorState("ethereum-main-collector", 9L, Instant.now());
        state.updateLastCollectedBlock(9L, "0xhash9");
        when(blockchainClient.fetchLatestBlockNumber()).thenReturn(12L);
        when(collectorStateRepository.findById(any())).thenReturn(Optional.of(state));
        when(blockchainClient.fetchBlock(anyLong())).thenAnswer(invocation -> block(invocation.getArgument(0)));
        when(blockProcessor.process(any())).thenReturn(List.of());

        service.collectUpToLatest();

        assertThat(state.getLastKnownChainHead()).isEqualTo(12L);
        verify(collectorStateRepository).save(state);
    }

    @Test
    void observedChainHeadNeverRegresses() {
        CollectorState state = new CollectorState("ethereum-main-collector", 95L, Instant.now());
        state.observeChainHead(100L);
        when(collectorStateRepository.findById(any())).thenReturn(Optional.of(state));

        service.collectUpTo(90L); // 이미 수집된 지점보다 낮은 target(지연 도착 head)

        assertThat(state.getLastKnownChainHead()).isEqualTo(100L);
    }

    @Test
    void reorgRewindKeepsObservedChainHead() {
        CollectorState state = new CollectorState("ethereum-main-collector", 99L, Instant.now());
        state.updateLastCollectedBlock(99L, "0xcanonical99");
        when(collectorStateRepository.findById(any())).thenReturn(Optional.of(state));
        BlockDto reorgedBlock = new BlockDto(100L, "0xhash100", "0xuncle99", Instant.EPOCH, "0xminer",
                BigInteger.ZERO, BigInteger.ZERO, NETWORK, List.of());
        when(blockchainClient.fetchBlock(100L)).thenReturn(reorgedBlock);

        service.collectUpTo(101L);

        // rewind는 수집 진행도만 되돌리고, head 관측치는 유지되어 rewind 구간이 "미확정"으로 판정된다.
        assertThat(state.getLastCollectedBlock()).isEqualTo(93L);
        assertThat(state.getLastKnownChainHead()).isEqualTo(101L);
    }

    /** parentHash가 직전 블록 해시와 연결되도록 생성해 연속성 검증을 통과시킨다. */
    private BlockDto block(long number) {
        return new BlockDto(number, "0xhash" + number, "0xhash" + (number - 1), Instant.EPOCH, "0xminer",
                BigInteger.ZERO, BigInteger.ZERO, NETWORK, List.of());
    }
}
