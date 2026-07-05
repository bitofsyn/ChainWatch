package com.chainwatch.backend.detection.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainwatch.backend.collector.kafka.RawTransactionEvent;
import com.chainwatch.backend.detection.service.DetectionService;
import com.chainwatch.backend.transaction.domain.Transaction;
import com.chainwatch.backend.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RawTransactionDetectionConsumerTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private DetectionService detectionService;

    private RawTransactionDetectionConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new RawTransactionDetectionConsumer(transactionRepository, detectionService);
    }

    @Test
    void reusesTransactionAlreadyPersistedByCollector() {
        Transaction existing = new Transaction(
                "0xtx", "0xfrom", "0xto", BigDecimal.ONE, BigDecimal.ZERO, 100L, Instant.EPOCH, null);
        when(transactionRepository.findByTxHash("0xtx")).thenReturn(Optional.of(existing));

        consumer.consume(event("0xtx", "0xto"));

        verify(transactionRepository, never()).save(any());
        verify(detectionService).analyzeTransaction(existing);
    }

    @Test
    void persistsTransactionWhenMissingAndMapsFields() {
        when(transactionRepository.findByTxHash("0xtx")).thenReturn(Optional.empty());
        when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        consumer.consume(event("0xtx", "0xto"));

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction saved = captor.getValue();
        assertThat(saved.getTxHash()).isEqualTo("0xtx");
        assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("150"));
        assertThat(saved.getGasFee()).isEqualByComparingTo(new BigDecimal("0.000021"));
        assertThat(saved.getBlockNumber()).isEqualTo(100L);
        verify(detectionService).analyzeTransaction(saved);
    }

    @Test
    void mapsContractCreationToSentinelAddress() {
        when(transactionRepository.findByTxHash("0xtx")).thenReturn(Optional.empty());
        when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        consumer.consume(event("0xtx", null));

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getToAddress()).isEqualTo("CONTRACT_CREATION");
    }

    private RawTransactionEvent event(String txHash, String toAddress) {
        return new RawTransactionEvent(
                txHash, 100L, "0xfrom", toAddress, new BigDecimal("150"),
                BigInteger.valueOf(21_000), BigInteger.valueOf(1_000_000_000L), null, null,
                BigInteger.ONE, "0x", "LEGACY", null, Instant.EPOCH, "ethereum-mainnet", Instant.EPOCH);
    }
}
