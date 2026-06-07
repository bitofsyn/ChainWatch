package com.chainwatch.backend.collector.client;

import com.chainwatch.backend.collector.config.EtherscanProperties;
import com.chainwatch.backend.collector.exception.CollectorException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.web3j.utils.Convert;

@Component
@ConditionalOnProperty(prefix = "chainwatch.collector", name = "provider", havingValue = "etherscan")
public class EtherscanBlockClient implements BlockClient {

    private final WebClient webClient;
    private final EtherscanProperties etherscanProperties;

    public EtherscanBlockClient(WebClient.Builder webClientBuilder, EtherscanProperties etherscanProperties) {
        this.webClient = webClientBuilder.baseUrl(etherscanProperties.baseUrl()).build();
        this.etherscanProperties = etherscanProperties;
    }

    @Override
    public long getLatestBlockNumber() throws IOException {
        try {
            EtherscanJsonRpcResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/api")
                            .queryParam("chainid", etherscanProperties.chainId())
                            .queryParam("module", "proxy")
                            .queryParam("action", "eth_blockNumber")
                            .queryParam("apikey", etherscanProperties.apiKey())
                            .build())
                    .retrieve()
                    .bodyToMono(EtherscanJsonRpcResponse.class)
                    .block();

            if (response == null || response.result() == null) {
                throw new CollectorException("Etherscan returned an empty latest block response");
            }

            return parseHexLong(response.result());
        } catch (RuntimeException exception) {
            throw new IOException("Failed to fetch latest block number from Etherscan", exception);
        }
    }

    @Override
    public CollectedBlock getBlock(long blockNumber) throws IOException {
        try {
            EtherscanBlockResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/api")
                            .queryParam("chainid", etherscanProperties.chainId())
                            .queryParam("module", "proxy")
                            .queryParam("action", "eth_getBlockByNumber")
                            .queryParam("tag", toHex(blockNumber))
                            .queryParam("boolean", "true")
                            .queryParam("apikey", etherscanProperties.apiKey())
                            .build())
                    .retrieve()
                    .bodyToMono(EtherscanBlockResponse.class)
                    .block();

            if (response == null || response.result() == null) {
                throw new CollectorException("Etherscan returned an empty block response for " + blockNumber);
            }

            EtherscanBlockResult result = response.result();
            List<CollectedTransaction> transactions = result.transactions() == null
                    ? List.of()
                    : result.transactions().stream()
                    .map(this::toCollectedTransaction)
                    .toList();

            return new CollectedBlock(
                    parseHexLong(result.number()),
                    Instant.ofEpochSecond(parseHexLong(result.timestamp())),
                    transactions
            );
        } catch (RuntimeException exception) {
            throw new IOException("Failed to fetch block " + blockNumber + " from Etherscan", exception);
        }
    }

    private CollectedTransaction toCollectedTransaction(EtherscanTransactionResult transaction) {
        String toAddress = transaction.to() == null ? "CONTRACT_CREATION" : transaction.to();
        return new CollectedTransaction(
                transaction.hash(),
                transaction.from(),
                toAddress,
                fromWei(parseHexBigInteger(transaction.value())),
                fromWei(parseHexBigInteger(transaction.gasPrice()).multiply(parseHexBigInteger(transaction.gas()))),
                inferContractAddress(transaction)
        );
    }

    private String inferContractAddress(EtherscanTransactionResult transaction) {
        if (transaction.to() == null) {
            return null;
        }

        String input = transaction.input();
        if (input != null && !"0x".equals(input)) {
            return transaction.to();
        }

        return null;
    }

    private BigDecimal fromWei(BigInteger value) {
        return Convert.fromWei(new BigDecimal(value), Convert.Unit.ETHER);
    }

    private long parseHexLong(String value) {
        return parseHexBigInteger(value).longValue();
    }

    private BigInteger parseHexBigInteger(String value) {
        return new BigInteger(value.replace("0x", ""), 16);
    }

    private String toHex(long value) {
        return "0x" + Long.toHexString(value);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EtherscanJsonRpcResponse(
            String jsonrpc,
            Integer id,
            String result
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EtherscanBlockResponse(
            String jsonrpc,
            Integer id,
            EtherscanBlockResult result
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EtherscanBlockResult(
            String number,
            String timestamp,
            List<EtherscanTransactionResult> transactions
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EtherscanTransactionResult(
            String hash,
            String from,
            String to,
            String value,
            String gas,
            String gasPrice,
            String input
    ) {
    }
}
