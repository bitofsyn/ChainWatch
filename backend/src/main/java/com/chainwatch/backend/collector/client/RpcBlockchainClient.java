package com.chainwatch.backend.collector.client;

import com.chainwatch.backend.collector.dto.BlockDto;
import com.chainwatch.backend.collector.exception.RpcClientException;
import com.chainwatch.backend.collector.mapper.EthereumBlockMapper;
import java.io.IOException;
import java.math.BigInteger;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlockNumber;

/**
 * Web3j 기반 Ethereum JSON-RPC 클라이언트 (HTTP).
 */
public class RpcBlockchainClient implements BlockchainClient {

    private final Web3j web3j;
    private final EthereumBlockMapper mapper;
    private final String network;

    public RpcBlockchainClient(Web3j web3j, EthereumBlockMapper mapper, String network) {
        this.web3j = web3j;
        this.mapper = mapper;
        this.network = network;
    }

    @Override
    public long fetchLatestBlockNumber() {
        try {
            EthBlockNumber response = web3j.ethBlockNumber().send();
            requireNoError("eth_blockNumber", response);
            return response.getBlockNumber().longValueExact();
        } catch (RpcClientException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new RpcClientException("Failed to fetch latest block number", exception);
        }
    }

    @Override
    public BlockDto fetchBlock(long blockNumber) {
        try {
            EthBlock response = web3j.ethGetBlockByNumber(
                            DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNumber)),
                            true
                    )
                    .send();
            requireNoError("eth_getBlockByNumber", response);

            EthBlock.Block block = response.getBlock();
            if (block == null) {
                throw new RpcClientException("Block not found: " + blockNumber);
            }
            return mapper.toBlockDto(block, network);
        } catch (RpcClientException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new RpcClientException("Failed to fetch block " + blockNumber, exception);
        }
    }

    @Override
    public String network() {
        return network;
    }

    private void requireNoError(String method, Response<?> response) {
        if (response.hasError()) {
            throw new RpcClientException(
                    "RPC error on %s: code=%d message=%s".formatted(
                            method, response.getError().getCode(), response.getError().getMessage()));
        }
    }
}
