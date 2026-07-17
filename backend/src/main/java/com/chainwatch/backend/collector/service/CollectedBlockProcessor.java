package com.chainwatch.backend.collector.service;

import com.chainwatch.backend.collector.domain.CollectorState;
import com.chainwatch.backend.collector.dto.BlockDto;
import com.chainwatch.backend.collector.dto.TransactionDto;
import com.chainwatch.backend.collector.repository.CollectorStateRepository;
import com.chainwatch.backend.collector.util.GasFees;
import com.chainwatch.backend.detection.config.DetectionProperties;
import com.chainwatch.backend.detection.service.DetectionService;
import com.chainwatch.backend.transaction.domain.Transaction;
import com.chainwatch.backend.transaction.repository.TransactionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집된 블록의 DB 반영을 담당한다: 트랜잭션 중복 제거·저장, 탐지 실행, 수집 상태 갱신.
 * 하나의 DB 트랜잭션으로 처리해 재시작 시 일관성을 보장한다.
 */
@Service
public class CollectedBlockProcessor {

    static final String COLLECTOR_NAME = "ethereum-main-collector";
    private static final String CONTRACT_CREATION_ADDRESS = "CONTRACT_CREATION";

    private final TransactionRepository transactionRepository;
    private final CollectorStateRepository collectorStateRepository;
    private final DetectionService detectionService;
    private final DetectionProperties detectionProperties;

    public CollectedBlockProcessor(
            TransactionRepository transactionRepository,
            CollectorStateRepository collectorStateRepository,
            DetectionService detectionService,
            DetectionProperties detectionProperties
    ) {
        this.transactionRepository = transactionRepository;
        this.collectorStateRepository = collectorStateRepository;
        this.detectionService = detectionService;
        this.detectionProperties = detectionProperties;
    }

    @Transactional
    public List<Transaction> process(BlockDto block) {
        List<Transaction> savedTransactions = saveNewTransactions(block);
        if (!detectionProperties.isKafkaMode()) {
            // KAFKA 모드에서는 RawTransactionDetectionConsumer가 raw-transactions 토픽에서 탐지를 수행한다.
            detectionService.analyzeTransactions(savedTransactions);
        }
        advanceCollectorState(block);
        return savedTransactions;
    }

    private List<Transaction> saveNewTransactions(BlockDto block) {
        List<String> txHashes = block.transactions().stream()
                .map(TransactionDto::txHash)
                .toList();
        Set<String> existingTxHashes = txHashes.isEmpty()
                ? Set.of()
                : transactionRepository.findByTxHashIn(txHashes).stream()
                        .map(Transaction::getTxHash)
                        .collect(Collectors.toSet());

        List<Transaction> newTransactions = block.transactions().stream()
                .filter(transaction -> !existingTxHashes.contains(transaction.txHash()))
                .map(this::toEntity)
                .toList();

        return transactionRepository.saveAll(newTransactions);
    }

    private Transaction toEntity(TransactionDto transaction) {
        return new Transaction(
                transaction.txHash(),
                transaction.fromAddress(),
                transaction.toAddress() == null ? CONTRACT_CREATION_ADDRESS : transaction.toAddress(),
                transaction.valueEth(),
                GasFees.estimateFeeEth(transaction.gasPriceWei(), transaction.maxFeePerGasWei(), transaction.gas()),
                transaction.blockNumber(),
                transaction.timestamp(),
                transaction.contractAddress(),
                transaction.network()
        );
    }

    private void advanceCollectorState(BlockDto block) {
        CollectorState state = collectorStateRepository.findById(COLLECTOR_NAME)
                .orElseGet(() -> new CollectorState(COLLECTOR_NAME, block.blockNumber(), Instant.now()));
        if (block.blockNumber() >= state.getLastCollectedBlock()) {
            state.updateLastCollectedBlock(block.blockNumber(), block.blockHash());
        }
        collectorStateRepository.save(state);
    }
}
