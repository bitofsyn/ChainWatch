package com.chainwatch.backend.collector.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainwatch.backend.collector.domain.CollectorState;
import com.chainwatch.backend.collector.dto.BlockDto;
import com.chainwatch.backend.collector.dto.TransactionDto;
import com.chainwatch.backend.collector.repository.CollectorStateRepository;
import com.chainwatch.backend.detection.config.DetectionProperties;
import com.chainwatch.backend.detection.service.DetectionService;
import com.chainwatch.backend.transaction.domain.Transaction;
import com.chainwatch.backend.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CollectedBlockProcessorTest {

    private static final String NETWORK = "ethereum-mainnet";

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CollectorStateRepository collectorStateRepository;

    @Mock
    private DetectionService detectionService;

    private CollectedBlockProcessor processor;

    @BeforeEach
    void setUp() {
        processor = processorWithMode(DetectionProperties.DetectionTransport.SYNC);
    }

    private CollectedBlockProcessor processorWithMode(DetectionProperties.DetectionTransport mode) {
        DetectionProperties detectionProperties = new DetectionProperties(
                mode, null, null, 0, 0, 0, 0, 0, List.of(), List.of());
        return new CollectedBlockProcessor(
                transactionRepository, collectorStateRepository, detectionService, detectionProperties);
    }

    @Test
    void savesOnlyNewTransactionsAndRunsDetection() {
        TransactionDto existing = transfer("0xexisting");
        TransactionDto fresh = transfer("0xfresh");
        BlockDto block = block(100, List.of(existing, fresh));

        when(transactionRepository.findByTxHashIn(anyList()))
                .thenReturn(List.of(entity("0xexisting")));
        when(transactionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(collectorStateRepository.findById(CollectedBlockProcessor.COLLECTOR_NAME))
                .thenReturn(Optional.empty());

        List<Transaction> saved = processor.process(block);

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getTxHash()).isEqualTo("0xfresh");
        verify(detectionService).analyzeTransactions(saved);
    }

    @Test
    void skipsSyncDetectionInKafkaMode() {
        processor = processorWithMode(DetectionProperties.DetectionTransport.KAFKA);
        when(transactionRepository.findByTxHashIn(anyList())).thenReturn(List.of());
        when(transactionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(collectorStateRepository.findById(any())).thenReturn(Optional.empty());

        processor.process(block(100, List.of(transfer("0xfresh"))));

        org.mockito.Mockito.verifyNoInteractions(detectionService);
    }

    @Test
    void advancesCollectorStateMonotonically() {
        BlockDto block = block(100, List.of());
        CollectorState state = new CollectorState(CollectedBlockProcessor.COLLECTOR_NAME, 150L, Instant.now());
        when(collectorStateRepository.findById(CollectedBlockProcessor.COLLECTOR_NAME))
                .thenReturn(Optional.of(state));
        when(transactionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        processor.process(block);

        ArgumentCaptor<CollectorState> captor = ArgumentCaptor.forClass(CollectorState.class);
        verify(collectorStateRepository).save(captor.capture());
        assertThat(captor.getValue().getLastCollectedBlock()).isEqualTo(150L);
    }

    @Test
    void computesGasFeeFromGasPriceOrMaxFee() {
        TransactionDto legacyPriced = new TransactionDto(
                "0xa", 100, "0xfrom", "0xto", BigDecimal.ONE,
                BigInteger.valueOf(21_000), BigInteger.valueOf(1_000_000_000L), null, null,
                BigInteger.ONE, "0x", "LEGACY", null, Instant.EPOCH, NETWORK);
        TransactionDto eip1559NoGasPrice = new TransactionDto(
                "0xb", 100, "0xfrom", null, BigDecimal.ONE,
                BigInteger.valueOf(21_000), null, BigInteger.valueOf(2_000_000_000L), null,
                BigInteger.ONE, "0x", "EIP1559", null, Instant.EPOCH, NETWORK);

        when(transactionRepository.findByTxHashIn(anyList())).thenReturn(List.of());
        when(transactionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(collectorStateRepository.findById(any())).thenReturn(Optional.empty());

        List<Transaction> saved = processor.process(block(100, List.of(legacyPriced, eip1559NoGasPrice)));

        assertThat(saved.get(0).getGasFee()).isEqualByComparingTo(new BigDecimal("0.000021"));
        assertThat(saved.get(1).getGasFee()).isEqualByComparingTo(new BigDecimal("0.000042"));
        assertThat(saved.get(1).getToAddress()).isEqualTo("CONTRACT_CREATION");
    }

    private BlockDto block(long number, List<TransactionDto> transactions) {
        return new BlockDto(number, "0xhash", "0xparent", Instant.EPOCH, "0xminer",
                BigInteger.ZERO, BigInteger.ZERO, NETWORK, transactions);
    }

    private TransactionDto transfer(String txHash) {
        return new TransactionDto(txHash, 100, "0xfrom", "0xto", BigDecimal.ONE,
                BigInteger.valueOf(21_000), BigInteger.valueOf(1_000_000_000L), null, null,
                BigInteger.ONE, "0x", "LEGACY", null, Instant.EPOCH, NETWORK);
    }

    private Transaction entity(String txHash) {
        return new Transaction(txHash, "0xfrom", "0xto", BigDecimal.ONE, BigDecimal.ZERO,
                100L, Instant.EPOCH, null);
    }
}
