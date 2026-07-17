package com.chainwatch.backend.detection.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chainwatch.backend.detection.config.DetectionProperties;
import com.chainwatch.backend.detection.domain.DetectionCommand;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.transaction.domain.Transaction;
import com.chainwatch.backend.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 각 룰이 "왜 발화했는가"를 구조화된 evidence(임계값·관측값·매칭 주소)로 남기는지 검증한다. */
class DetectionRuleEvidenceTest {

    private static final String EXCHANGE_ADDRESS = "0xexchange";
    private static final String WATCHLIST_ADDRESS = "0xwatched";

    private final DetectionProperties properties = new DetectionProperties(
            DetectionProperties.DetectionTransport.SYNC,
            new BigDecimal("100.0"),
            new BigDecimal("50.0"),
            3,
            10,
            5,
            15,
            List.of(WATCHLIST_ADDRESS),
            List.of(EXCHANGE_ADDRESS)
    );

    @Test
    void largeTransferRecordsThresholdAndObservedAmount() {
        Transaction transaction = transaction("0xfrom", "0xto", new BigDecimal("250"));

        DetectionCommand command = new LargeTransferDetectionRule(properties)
                .evaluate(transaction).orElseThrow();

        assertThat(command.ruleName()).isEqualTo("large-transfer");
        assertThat(command.ruleVersion()).isEqualTo("1.0");
        assertThat(command.evidence()).containsEntry("thresholdEth", new BigDecimal("100.0"));
        assertThat(command.evidence()).containsEntry("observedAmountEth", new BigDecimal("250"));
        assertThat(command.evidence()).containsEntry("fromAddress", "0xfrom");
        assertThat(command.evidence()).containsEntry("toAddress", "0xto");
    }

    @Test
    void rapidTransferRecordsWindowCounts() {
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        when(transactionRepository.countRecentTransfersFromAddress(anyString(), any())).thenReturn(5L);
        Transaction transaction = transaction("0xburst", "0xto", BigDecimal.ONE);

        DetectionCommand command = new RapidTransferDetectionRule(properties, transactionRepository)
                .evaluate(transaction).orElseThrow();

        assertThat(command.ruleName()).isEqualTo("rapid-transfer");
        assertThat(command.evidence()).containsEntry("windowMinutes", 10L);
        assertThat(command.evidence()).containsEntry("thresholdCount", 3);
        assertThat(command.evidence()).containsEntry("observedTransferCount", 5L);
        assertThat(command.evidence()).containsEntry("fromAddress", "0xburst");
        assertThat(command.evidence()).containsKey("windowStart");
    }

    @Test
    void exchangeFlowRecordsDirectionAndMatchedExchangeAddress() {
        Transaction inbound = transaction("0xuser", EXCHANGE_ADDRESS, new BigDecimal("80"));

        DetectionCommand command = new ExchangeFlowDetectionRule(properties)
                .evaluate(inbound).orElseThrow();

        assertThat(command.eventType()).isEqualTo(EventType.EXCHANGE_FLOW);
        assertThat(command.ruleName()).isEqualTo("exchange-flow");
        assertThat(command.evidence()).containsEntry("direction", "INBOUND");
        assertThat(command.evidence()).containsEntry("matchedExchangeAddress", EXCHANGE_ADDRESS);
        assertThat(command.evidence()).containsEntry("counterpartyAddress", "0xuser");
        assertThat(command.evidence()).containsEntry("observedAmountEth", new BigDecimal("80"));
    }

    @Test
    void exchangeFlowOutboundDirectionIsRecorded() {
        Transaction outbound = transaction(EXCHANGE_ADDRESS, "0xuser", new BigDecimal("80"));

        DetectionCommand command = new ExchangeFlowDetectionRule(properties)
                .evaluate(outbound).orElseThrow();

        assertThat(command.evidence()).containsEntry("direction", "OUTBOUND");
        assertThat(command.evidence()).containsEntry("matchedExchangeAddress", EXCHANGE_ADDRESS);
        assertThat(command.evidence()).containsEntry("counterpartyAddress", "0xuser");
    }

    @Test
    void watchlistActivityRecordsMatchedAddressDirectionAndReason() {
        Transaction transaction = transaction(WATCHLIST_ADDRESS, "0xother", BigDecimal.ONE);

        DetectionCommand command = new WatchlistActivityDetectionRule(properties)
                .evaluate(transaction).orElseThrow();

        assertThat(command.ruleName()).isEqualTo("watchlist-activity");
        assertThat(command.evidence()).containsEntry("matchedAddress", WATCHLIST_ADDRESS);
        assertThat(command.evidence()).containsEntry("matchedDirection", "FROM");
        assertThat(command.evidence()).containsEntry("watchlistReason", "configured-watchlist-address");
        assertThat(command.evidence()).containsEntry("counterpartyAddress", "0xother");
    }

    @Test
    void fanOutFiresWhenDistinctRecipientsReachThresholdAndRecordsOutDegree() {
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        when(transactionRepository.countDistinctRecipientsFromAddress(anyString(), any())).thenReturn(6L);
        Transaction transaction = transaction("0xsplitter", "0xr1", BigDecimal.ONE);

        DetectionCommand command = new FanOutDetectionRule(properties, transactionRepository)
                .evaluate(transaction).orElseThrow();

        assertThat(command.eventType()).isEqualTo(EventType.FAN_OUT);
        assertThat(command.ruleName()).isEqualTo("fan-out");
        assertThat(command.evidence()).containsEntry("windowMinutes", 15L);
        assertThat(command.evidence()).containsEntry("thresholdRecipients", 5);
        assertThat(command.evidence()).containsEntry("observedDistinctRecipients", 6L);
        assertThat(command.evidence()).containsEntry("fromAddress", "0xsplitter");
    }

    @Test
    void fanOutDoesNotFireBelowRecipientThreshold() {
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        // 같은 상대에게 반복 송금(높은 빈도, 낮은 out-degree)은 fan-out으로 잡히지 않는다
        when(transactionRepository.countDistinctRecipientsFromAddress(anyString(), any())).thenReturn(2L);
        Transaction transaction = transaction("0xrepeat", "0xsame", BigDecimal.ONE);

        assertThat(new FanOutDetectionRule(properties, transactionRepository).evaluate(transaction))
                .isEmpty();
    }

    private Transaction transaction(String from, String to, BigDecimal amount) {
        return new Transaction("0xtx", from, to, amount, BigDecimal.ZERO, 100L, Instant.now(), null);
    }
}
