package com.chainwatch.backend.collector.service;

import com.chainwatch.backend.collector.api.CollectorResponse;
import com.chainwatch.backend.collector.client.BlockchainClient;
import com.chainwatch.backend.collector.config.CollectorProperties;
import com.chainwatch.backend.collector.domain.CollectorState;
import com.chainwatch.backend.collector.dto.BlockDto;
import com.chainwatch.backend.collector.kafka.RawEventPublisher;
import com.chainwatch.backend.collector.metrics.CollectorMetrics;
import com.chainwatch.backend.collector.repository.CollectorStateRepository;
import com.chainwatch.backend.transaction.domain.Transaction;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 블록 수집 오케스트레이션: 다음 수집 대상 결정 → 블록 조회 → 저장/탐지 → Kafka 발행.
 * 폴링 스케줄러, WebSocket 구독자, 수동 API가 공통으로 사용한다.
 */
@Service
public class BlockCollectionService {

    private static final Logger log = LoggerFactory.getLogger(BlockCollectionService.class);

    private final BlockchainClient blockchainClient;
    private final CollectorProperties collectorProperties;
    private final CollectorStateRepository collectorStateRepository;
    private final CollectedBlockProcessor blockProcessor;
    private final RawEventPublisher rawEventPublisher;
    private final CollectorMetrics metrics;

    /** 폴링·WebSocket·수동 트리거가 겹칠 때 catch-up 루프의 중복 실행을 막는다. */
    private final AtomicBoolean collecting = new AtomicBoolean(false);

    public BlockCollectionService(
            BlockchainClient blockchainClient,
            CollectorProperties collectorProperties,
            CollectorStateRepository collectorStateRepository,
            CollectedBlockProcessor blockProcessor,
            RawEventPublisher rawEventPublisher,
            CollectorMetrics metrics
    ) {
        this.blockchainClient = blockchainClient;
        this.collectorProperties = collectorProperties;
        this.collectorStateRepository = collectorStateRepository;
        this.blockProcessor = blockProcessor;
        this.rawEventPublisher = rawEventPublisher;
        this.metrics = metrics;
    }

    /**
     * 마지막 수집 지점 다음 블록부터 체인 최신 블록까지 수집한다 (배치 상한 적용).
     *
     * @return 이번 호출에서 수집한 블록 수
     */
    public int collectUpToLatest() {
        return collectUpTo(blockchainClient.fetchLatestBlockNumber());
    }

    /**
     * 마지막 수집 지점 다음 블록부터 {@code targetBlockNumber}까지 순서대로 수집한다.
     * 재시작 후에도 저장된 상태에서 이어서 수집하므로 중복·누락이 없다.
     */
    public int collectUpTo(long targetBlockNumber) {
        if (!collecting.compareAndSet(false, true)) {
            log.debug("Collection already in progress, skipping trigger up to block {}", targetBlockNumber);
            return 0;
        }
        try {
            recordObservedChainHead(targetBlockNumber);
            long next = nextBlockNumber(targetBlockNumber);
            String expectedParentHash = expectedParentHashFor(next);
            int processed = 0;
            while (next <= targetBlockNumber && processed < collectorProperties.maxBlocksPerPoll()) {
                BlockDto block = blockchainClient.fetchBlock(next);
                if (isReorged(expectedParentHash, block)) {
                    rewindAfterReorg(block, expectedParentHash);
                    return processed;
                }
                processFetchedBlock(block);
                expectedParentHash = block.blockHash();
                next++;
                processed++;
            }
            if (next <= targetBlockNumber) {
                log.info("Collector is catching up: {} blocks remaining (next={}, head={})",
                        targetBlockNumber - next + 1, next, targetBlockNumber);
            }
            return processed;
        } finally {
            collecting.set(false);
        }
    }

    public CollectorResponse collectLatestBlock() {
        return collectSingleBlock(blockchainClient.fetchLatestBlockNumber());
    }

    public CollectorResponse collectBlock(long blockNumber) {
        return collectSingleBlock(blockNumber);
    }

    public long lastCollectedBlockNumber() {
        return collectorStateRepository.findById(CollectedBlockProcessor.COLLECTOR_NAME)
                .map(CollectorState::getLastCollectedBlock)
                .orElse(-1L);
    }

    /**
     * 체인 head 관측치를 상태에 기록한다(단조 증가). confirmations 계산의 기준값이 된다.
     * collectUpTo의 target은 폴러(fetchLatestBlockNumber)든 WebSocket(신규 head 블록)이든 항상 체인 head다.
     * 상태 행이 아직 없으면(최초 기동) 이번 사이클의 블록 처리로 행이 생기고 다음 사이클부터 기록된다.
     */
    private void recordObservedChainHead(long chainHead) {
        collectorStateRepository.findById(CollectedBlockProcessor.COLLECTOR_NAME)
                .ifPresent(state -> {
                    if (state.observeChainHead(chainHead)) {
                        collectorStateRepository.save(state);
                    }
                });
    }

    /** 직전 수집 블록의 해시. 다음 블록의 parentHash와 일치해야 정상 체인이다. */
    private String expectedParentHashFor(long nextBlockNumber) {
        return collectorStateRepository.findById(CollectedBlockProcessor.COLLECTOR_NAME)
                .filter(state -> state.getLastCollectedBlock() == nextBlockNumber - 1)
                .map(CollectorState::getLastCollectedBlockHash)
                .orElse(null);
    }

    private boolean isReorged(String expectedParentHash, BlockDto block) {
        return expectedParentHash != null && !expectedParentHash.equals(block.parentHash());
    }

    /**
     * reorg 감지 시 설정된 깊이만큼 되감아 다음 사이클에 정규 체인을 다시 수집한다.
     * 트랜잭션은 txHash로 dedupe되므로 재수집이 안전하다(고아 트랜잭션 정리는 추후 과제).
     */
    private void rewindAfterReorg(BlockDto block, String expectedParentHash) {
        long rewindTo = Math.max(0, block.blockNumber() - 1 - collectorProperties.reorgRewindDepth());
        metrics.incrementReorg();
        log.warn("[REORG] Chain reorganization detected at block {}: expected parent {} but got {}. "
                        + "Rewinding to block {} (depth={})",
                block.blockNumber(), expectedParentHash, block.parentHash(),
                rewindTo, collectorProperties.reorgRewindDepth());
        collectorStateRepository.findById(CollectedBlockProcessor.COLLECTOR_NAME)
                .ifPresent(state -> {
                    state.rewindTo(rewindTo);
                    collectorStateRepository.save(state);
                });
    }

    private CollectorResponse collectSingleBlock(long blockNumber) {
        BlockDto block = blockchainClient.fetchBlock(blockNumber);
        return processFetchedBlock(block);
    }

    private CollectorResponse processFetchedBlock(BlockDto block) {
        log.info("[BLOCK_RECEIVED] number={} hash={} transactions={}",
                block.blockNumber(), block.blockHash(), block.transactionCount());

        List<Transaction> savedTransactions = blockProcessor.process(block);
        log.info("[TRANSACTION_PARSED] block={} parsed={} saved={}",
                block.blockNumber(), block.transactionCount(), savedTransactions.size());

        rawEventPublisher.publishBlock(block);
        metrics.recordBlockCollected(block.transactionCount());

        return new CollectorResponse(
                block.blockNumber(),
                block.transactionCount(),
                savedTransactions.size(),
                block.network(),
                collectorProperties.provider().name()
        );
    }

    private long nextBlockNumber(long chainHead) {
        return collectorStateRepository.findById(CollectedBlockProcessor.COLLECTOR_NAME)
                .map(state -> state.getLastCollectedBlock() + 1)
                .orElseGet(() -> collectorProperties.startBlock() > 0
                        ? collectorProperties.startBlock()
                        : chainHead);
    }
}
