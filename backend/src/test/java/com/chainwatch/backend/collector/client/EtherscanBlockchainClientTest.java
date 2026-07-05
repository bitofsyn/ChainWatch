package com.chainwatch.backend.collector.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chainwatch.backend.collector.config.EtherscanProperties;
import com.chainwatch.backend.collector.dto.BlockDto;
import com.chainwatch.backend.collector.dto.TransactionDto;
import com.chainwatch.backend.collector.exception.RpcClientException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class EtherscanBlockchainClientTest {

    private static final String NETWORK = "ethereum-mainnet";

    private MockWebServer server;
    private EtherscanBlockchainClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        EtherscanProperties properties = new EtherscanProperties(
                server.url("/").toString(), "test-api-key", "1");
        client = new EtherscanBlockchainClient(WebClient.builder(), properties, NETWORK);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void fetchesLatestBlockNumber() {
        enqueueJson("""
                {"jsonrpc":"2.0","id":1,"result":"0x10"}
                """);

        assertThat(client.fetchLatestBlockNumber()).isEqualTo(16L);
    }

    @Test
    void fetchesBlockWithFullTransactionFields() {
        enqueueJson("""
                {"jsonrpc":"2.0","id":1,"result":{
                  "number":"0x10",
                  "hash":"0xblockhash",
                  "parentHash":"0xparenthash",
                  "timestamp":"0x6553f100",
                  "miner":"0xminer",
                  "gasUsed":"0x5208",
                  "gasLimit":"0x1c9c380",
                  "transactions":[{
                    "hash":"0xtxhash",
                    "from":"0xfrom",
                    "to":"0xto",
                    "value":"0xde0b6b3a7640000",
                    "gas":"0x5208",
                    "gasPrice":"0x4a817c800",
                    "maxFeePerGas":"0x6fc23ac00",
                    "maxPriorityFeePerGas":"0x3b9aca00",
                    "nonce":"0x7",
                    "input":"0xa9059cbb",
                    "type":"0x2"
                  }]
                }}
                """);

        BlockDto block = client.fetchBlock(16L);

        assertThat(block.blockNumber()).isEqualTo(16L);
        assertThat(block.blockHash()).isEqualTo("0xblockhash");
        assertThat(block.parentHash()).isEqualTo("0xparenthash");
        assertThat(block.miner()).isEqualTo("0xminer");
        assertThat(block.gasUsed()).isEqualTo(BigInteger.valueOf(21_000));
        assertThat(block.timestamp()).isEqualTo(Instant.ofEpochSecond(0x6553f100L));
        assertThat(block.network()).isEqualTo(NETWORK);

        TransactionDto transaction = block.transactions().get(0);
        assertThat(transaction.txHash()).isEqualTo("0xtxhash");
        assertThat(transaction.valueEth()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(transaction.maxFeePerGasWei()).isEqualTo(BigInteger.valueOf(30_000_000_000L));
        assertThat(transaction.transactionType()).isEqualTo("EIP1559");
        assertThat(transaction.contractAddress()).isEqualTo("0xto");
        assertThat(transaction.nonce()).isEqualTo(BigInteger.valueOf(7));
    }

    @Test
    void rateLimitResponseBecomesRetryableException() {
        enqueueJson("""
                {"status":"0","message":"NOTOK","result":"Max rate limit reached"}
                """);

        assertThatThrownBy(() -> client.fetchLatestBlockNumber())
                .isInstanceOf(RpcClientException.class);
    }

    @Test
    void emptyBlockResponseBecomesRetryableException() {
        enqueueJson("""
                {"jsonrpc":"2.0","id":1,"result":null}
                """);

        assertThatThrownBy(() -> client.fetchBlock(16L))
                .isInstanceOf(RpcClientException.class)
                .hasMessageContaining("16");
    }

    private void enqueueJson(String body) {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body));
    }
}
