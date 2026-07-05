package com.chainwatch.backend.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.chainwatch.backend.collector.kafka.RawTransactionEvent;
import com.chainwatch.backend.feed.service.FeedCacheService;
import com.chainwatch.backend.messaging.producer.CollectedTransactionMessage;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeedCacheConsumerTest {

    @Mock
    private FeedCacheService feedCacheService;

    @InjectMocks
    private FeedCacheConsumer consumer;

    @Test
    void mapsRawTransactionToFeedContract() {
        RawTransactionEvent event = new RawTransactionEvent(
                "0xtx", 42L, "0xfrom", "0xto", new BigDecimal("1.5"),
                BigInteger.valueOf(21_000), BigInteger.valueOf(1_000_000_000L), null, null,
                BigInteger.ONE, "0x", "LEGACY", "0xcontract",
                Instant.ofEpochSecond(1_700_000_000L), "ethereum-mainnet", Instant.now());

        consumer.consumeRawTransaction(event);

        ArgumentCaptor<CollectedTransactionMessage> captor =
                ArgumentCaptor.forClass(CollectedTransactionMessage.class);
        verify(feedCacheService).cacheTransaction(captor.capture());
        CollectedTransactionMessage message = captor.getValue();
        assertThat(message.transactionId()).isNull();
        assertThat(message.txHash()).isEqualTo("0xtx");
        assertThat(message.amount()).isEqualByComparingTo(new BigDecimal("1.5"));
        assertThat(message.gasFee()).isEqualByComparingTo(new BigDecimal("0.000021"));
        assertThat(message.blockNumber()).isEqualTo(42L);
        assertThat(message.timestamp()).isEqualTo(Instant.ofEpochSecond(1_700_000_000L));
        assertThat(message.contractAddress()).isEqualTo("0xcontract");
    }
}
