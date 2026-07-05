package com.chainwatch.backend.collector.client;

import com.chainwatch.backend.collector.config.EtherscanProperties;
import com.chainwatch.backend.collector.dto.BlockDto;
import com.chainwatch.backend.collector.dto.TransactionDto;
import com.chainwatch.backend.collector.exception.RpcClientException;
import com.chainwatch.backend.collector.util.HexValues;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.web.reactive.function.client.WebClient;
import org.web3j.utils.Convert;

/**
 * Etherscan proxy API 기반 클라이언트. RPC 노드를 쓸 수 없는 환경의 대체 공급자.
 */
public class EtherscanBlockchainClient implements BlockchainClient {

    private static final String EMPTY_INPUT = "0x";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final WebClient webClient;
    private final EtherscanProperties etherscanProperties;
    private final String network;

    public EtherscanBlockchainClient(
            WebClient.Builder webClientBuilder,
            EtherscanProperties etherscanProperties,
            String network
    ) {
        this.webClient = webClientBuilder.baseUrl(etherscanProperties.baseUrl()).build();
        this.etherscanProperties = etherscanProperties;
        this.network = network;
    }

    @Override
    public long fetchLatestBlockNumber() {
        try {
            LatestBlockResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/api")
                            .queryParam("chainid", etherscanProperties.chainId())
                            .queryParam("module", "proxy")
                            .queryParam("action", "eth_blockNumber")
                            .queryParam("apikey", etherscanProperties.apiKey())
                            .build())
                    .retrieve()
                    .bodyToMono(LatestBlockResponse.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();

            if (response == null || response.result() == null) {
                throw new RpcClientException("Etherscan returned an empty latest block response");
            }
            return HexValues.toLong(response.result());
        } catch (RpcClientException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RpcClientException("Failed to fetch latest block number from Etherscan", exception);
        }
    }

    @Override
    public BlockDto fetchBlock(long blockNumber) {
        try {
            BlockResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/api")
                            .queryParam("chainid", etherscanProperties.chainId())
                            .queryParam("module", "proxy")
                            .queryParam("action", "eth_getBlockByNumber")
                            .queryParam("tag", HexValues.toHex(blockNumber))
                            .queryParam("boolean", "true")
                            .queryParam("apikey", etherscanProperties.apiKey())
                            .build())
                    .retrieve()
                    .bodyToMono(BlockResponse.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();

            if (response == null || response.result() == null) {
                throw new RpcClientException("Etherscan returned an empty block response for " + blockNumber);
            }
            return toBlockDto(response.result());
        } catch (RpcClientException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RpcClientException("Failed to fetch block " + blockNumber + " from Etherscan", exception);
        }
    }

    @Override
    public String network() {
        return network;
    }

    private BlockDto toBlockDto(BlockResult result) {
        long blockNumber = HexValues.toLong(result.number());
        Instant timestamp = Instant.ofEpochSecond(HexValues.toLong(result.timestamp()));

        List<TransactionDto> transactions = result.transactions() == null
                ? List.of()
                : result.transactions().stream()
                .map(transaction -> toTransactionDto(transaction, blockNumber, timestamp))
                .toList();

        return new BlockDto(
                blockNumber,
                result.hash(),
                result.parentHash(),
                timestamp,
                result.miner(),
                HexValues.toBigIntegerOrNull(result.gasUsed()),
                HexValues.toBigIntegerOrNull(result.gasLimit()),
                network,
                transactions
        );
    }

    private TransactionDto toTransactionDto(TransactionResult transaction, long blockNumber, Instant timestamp) {
        return new TransactionDto(
                transaction.hash(),
                blockNumber,
                transaction.from(),
                transaction.to(),
                weiToEth(HexValues.toBigIntegerOrNull(transaction.value())),
                HexValues.toBigIntegerOrNull(transaction.gas()),
                HexValues.toBigIntegerOrNull(transaction.gasPrice()),
                HexValues.toBigIntegerOrNull(transaction.maxFeePerGas()),
                HexValues.toBigIntegerOrNull(transaction.maxPriorityFeePerGas()),
                HexValues.toBigIntegerOrNull(transaction.nonce()),
                transaction.input(),
                mapTransactionType(transaction.type()),
                resolveContractAddress(transaction),
                timestamp,
                network
        );
    }

    private BigDecimal weiToEth(BigInteger wei) {
        if (wei == null) {
            return BigDecimal.ZERO;
        }
        return Convert.fromWei(new BigDecimal(wei), Convert.Unit.ETHER);
    }

    private String mapTransactionType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return "LEGACY";
        }
        return switch (rawType) {
            case "0x0" -> "LEGACY";
            case "0x1" -> "ACCESS_LIST";
            case "0x2" -> "EIP1559";
            case "0x3" -> "BLOB";
            case "0x4" -> "SET_CODE";
            default -> rawType;
        };
    }

    private String resolveContractAddress(TransactionResult transaction) {
        if (transaction.to() == null) {
            return null;
        }
        String input = transaction.input();
        if (input != null && !input.isBlank() && !EMPTY_INPUT.equals(input)) {
            return transaction.to();
        }
        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LatestBlockResponse(String jsonrpc, Integer id, String result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BlockResponse(String jsonrpc, Integer id, BlockResult result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BlockResult(
            String number,
            String hash,
            String parentHash,
            String timestamp,
            String miner,
            String gasUsed,
            String gasLimit,
            List<TransactionResult> transactions
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TransactionResult(
            String hash,
            String from,
            String to,
            String value,
            String gas,
            String gasPrice,
            String maxFeePerGas,
            String maxPriorityFeePerGas,
            String nonce,
            String input,
            String type
    ) {
    }
}
