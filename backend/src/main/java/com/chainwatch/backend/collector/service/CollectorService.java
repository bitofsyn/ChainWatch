package com.chainwatch.backend.collector.service;

import com.chainwatch.backend.collector.api.CollectorResponse;
import com.chainwatch.backend.collector.client.BlockClient;
import com.chainwatch.backend.collector.client.CollectedBlock;
import com.chainwatch.backend.collector.client.CollectedTransaction;
import com.chainwatch.backend.collector.config.CollectorProperties;
import com.chainwatch.backend.collector.domain.CollectorState;
import com.chainwatch.backend.collector.exception.CollectorException;
import com.chainwatch.backend.collector.repository.CollectorStateRepository;
import com.chainwatch.backend.detection.service.DetectionService;
import com.chainwatch.backend.transaction.domain.Transaction;
import com.chainwatch.backend.transaction.repository.TransactionRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CollectorService {

    private static final String COLLECTOR_NAME = "ethereum-main-collector";

    private final BlockClient blockClient;
    private final CollectorProperties collectorProperties;
    private final CollectorStateRepository collectorStateRepository;
    private final TransactionRepository transactionRepository;
    private final DetectionService detectionService;

    public CollectorService(
            BlockClient blockClient,
            CollectorProperties collectorProperties,
            CollectorStateRepository collectorStateRepository,
            TransactionRepository transactionRepository,
            DetectionService detectionService
    ) {
        this.blockClient = blockClient;
        this.collectorProperties = collectorProperties;
        this.collectorStateRepository = collectorStateRepository;
        this.transactionRepository = transactionRepository;
        this.detectionService = detectionService;
    }

    @Transactional
    public CollectorResponse collectLatestBlock() throws IOException {
        return collectBlock(blockClient.getLatestBlockNumber());
    }

    @Transactional
    public CollectorResponse collectNextBlock() {
        try {
            long nextBlockNumber = getNextBlockNumber();
            return collectBlock(nextBlockNumber);
        } catch (IOException exception) {
            throw new CollectorException("Failed to collect next block from Ethereum RPC", exception);
        }
    }

    @Transactional
    public CollectorResponse collectBlock(long blockNumber) throws IOException {
        CollectedBlock block = blockClient.getBlock(blockNumber);

        List<Transaction> newTransactions = new ArrayList<>();
        for (CollectedTransaction transactionData : block.transactions()) {
            if (transactionRepository.findByTxHash(transactionData.txHash()).isPresent()) {
                continue;
            }

            newTransactions.add(toTransaction(block, transactionData));
        }

        List<Transaction> savedTransactions = transactionRepository.saveAll(newTransactions);
        detectionService.analyzeTransactions(savedTransactions);
        updateCollectorState(block.blockNumber());
        return new CollectorResponse(
                block.blockNumber(),
                savedTransactions.size(),
                collectorProperties.provider()
        );
    }

    private Transaction toTransaction(CollectedBlock block, CollectedTransaction transactionData) {
        return new Transaction(
                transactionData.txHash(),
                transactionData.fromAddress(),
                transactionData.toAddress(),
                transactionData.amount(),
                transactionData.gasFee(),
                block.blockNumber(),
                block.timestamp(),
                transactionData.contractAddress()
        );
    }

    private long getNextBlockNumber() throws IOException {
        return collectorStateRepository.findById(COLLECTOR_NAME)
                .map(state -> state.getLastCollectedBlock() + 1)
                .orElseGet(collectorProperties::startBlock);
    }

    private void updateCollectorState(long blockNumber) {
        CollectorState collectorState = collectorStateRepository.findById(COLLECTOR_NAME)
                .orElseGet(() -> new CollectorState(COLLECTOR_NAME, blockNumber, Instant.now()));
        collectorState.updateLastCollectedBlock(blockNumber);
        collectorStateRepository.save(collectorState);
    }
}
