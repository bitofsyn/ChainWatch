package com.chainwatch.backend.collector.client;

import com.chainwatch.backend.collector.exception.CollectorException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.utils.Convert;

@Component
@ConditionalOnBean(Web3j.class)
@ConditionalOnProperty(prefix = "chainwatch.collector", name = "provider", havingValue = "rpc", matchIfMissing = true)
public class RpcBlockClient implements BlockClient {

    private final Web3j web3j;

    public RpcBlockClient(Web3j web3j) {
        this.web3j = web3j;
    }

    @Override
    public long getLatestBlockNumber() throws IOException {
        return web3j.ethBlockNumber().send().getBlockNumber().longValue();
    }

    @Override
    public CollectedBlock getBlock(long blockNumber) throws IOException {
        EthBlock.Block block = web3j.ethGetBlockByNumber(
                        DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNumber)),
                        true
                )
                .send()
                .getBlock();

        if (block == null) {
            throw new CollectorException("Block not found: " + blockNumber);
        }

        List<CollectedTransaction> transactions = block.getTransactions().stream()
                .map(EthBlock.TransactionResult::get)
                .filter(EthBlock.TransactionObject.class::isInstance)
                .map(EthBlock.TransactionObject.class::cast)
                .map(this::toCollectedTransaction)
                .toList();

        return new CollectedBlock(
                block.getNumber().longValue(),
                Instant.ofEpochSecond(block.getTimestamp().longValue()),
                transactions
        );
    }

    private CollectedTransaction toCollectedTransaction(EthBlock.TransactionObject transactionObject) {
        String toAddress = transactionObject.getTo() == null ? "CONTRACT_CREATION" : transactionObject.getTo();
        return new CollectedTransaction(
                transactionObject.getHash(),
                transactionObject.getFrom(),
                toAddress,
                fromWei(transactionObject.getValue()),
                fromWei(transactionObject.getGasPrice().multiply(transactionObject.getGas())),
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
