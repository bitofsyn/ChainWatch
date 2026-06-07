package com.chainwatch.backend.collector.service;

import com.chainwatch.backend.collector.api.CollectorResponse;
import com.chainwatch.backend.collector.config.EthereumProperties;
import com.chainwatch.backend.transaction.domain.Transaction;
import com.chainwatch.backend.transaction.repository.TransactionRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.utils.Convert;

@Service
@ConditionalOnProperty(prefix = "chainwatch.ethereum", name = "rpc-url")
public class CollectorService {

    private final Web3j web3j;
    private final EthereumProperties ethereumProperties;
    private final TransactionRepository transactionRepository;

    public CollectorService(
            Web3j web3j,
            EthereumProperties ethereumProperties,
            TransactionRepository transactionRepository
    ) {
        this.web3j = web3j;
        this.ethereumProperties = ethereumProperties;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public CollectorResponse collectLatestBlock() throws IOException {
        BigInteger latestBlockNumber = web3j.ethBlockNumber().send().getBlockNumber();
        return collectBlock(latestBlockNumber.longValue());
    }

    @Transactional
    public CollectorResponse collectBlock(long blockNumber) throws IOException {
        EthBlock.Block block = web3j.ethGetBlockByNumber(
                        DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNumber)),
                        true
                )
                .send()
                .getBlock();

        if (block == null) {
            throw new IllegalArgumentException("Block not found: " + blockNumber);
        }

        List<Transaction> newTransactions = new ArrayList<>();
        for (EthBlock.TransactionResult<?> result : block.getTransactions()) {
            Object value = result.get();
            if (!(value instanceof EthBlock.TransactionObject transactionObject)) {
                continue;
            }

            if (transactionRepository.findByTxHash(transactionObject.getHash()).isPresent()) {
                continue;
            }

            newTransactions.add(toTransaction(block, transactionObject));
        }

        transactionRepository.saveAll(newTransactions);
        return new CollectorResponse(
                block.getNumber().longValue(),
                newTransactions.size(),
                ethereumProperties.network()
        );
    }

    private Transaction toTransaction(EthBlock.Block block, EthBlock.TransactionObject transactionObject) {
        return new Transaction(
                transactionObject.getHash(),
                transactionObject.getFrom(),
                transactionObject.getTo() == null ? "CONTRACT_CREATION" : transactionObject.getTo(),
                fromWei(transactionObject.getValue()),
                fromWei(transactionObject.getGasPrice().multiply(transactionObject.getGas())),
                block.getNumber().longValue(),
                Instant.ofEpochSecond(block.getTimestamp().longValue()),
                inferContractAddress(transactionObject)
        );
    }

    private BigDecimal fromWei(BigInteger value) {
        return Convert.fromWei(new BigDecimal(value), Convert.Unit.ETHER);
    }

    private String inferContractAddress(EthBlock.TransactionObject transactionObject) {
        if (transactionObject.getTo() == null) {
            return null;
        }

        String input = transactionObject.getInput();
        if (input != null && !input.equals("0x")) {
            return transactionObject.getTo();
        }

        return null;
    }
}
