package com.chainwatch.backend.collector.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.chainwatch.backend.collector.dto.BlockDto;
import com.chainwatch.backend.collector.dto.TransactionDto;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.EthBlock;

class EthereumBlockMapperTest {

    private static final String NETWORK = "ethereum-mainnet";

    private final EthereumBlockMapper mapper = new EthereumBlockMapper();

    @Test
    void mapsBlockFields() {
        EthBlock.Block block = block("0x10", 1_700_000_000L, List.of());

        BlockDto blockDto = mapper.toBlockDto(block, NETWORK);

        assertThat(blockDto.blockNumber()).isEqualTo(16L);
        assertThat(blockDto.blockHash()).isEqualTo("0xblockhash");
        assertThat(blockDto.parentHash()).isEqualTo("0xparenthash");
        assertThat(blockDto.timestamp()).isEqualTo(Instant.ofEpochSecond(1_700_000_000L));
        assertThat(blockDto.miner()).isEqualTo("0xminer");
        assertThat(blockDto.gasUsed()).isEqualTo(BigInteger.valueOf(21_000));
        assertThat(blockDto.gasLimit()).isEqualTo(BigInteger.valueOf(30_000_000));
        assertThat(blockDto.network()).isEqualTo(NETWORK);
        assertThat(blockDto.transactionCount()).isZero();
    }

    @Test
    void mapsEip1559Transfer() {
        EthBlock.TransactionObject transaction = eip1559Transfer();
        EthBlock.Block block = block("0x10", 1_700_000_000L, List.of(transaction));

        BlockDto blockDto = mapper.toBlockDto(block, NETWORK);

        assertThat(blockDto.transactionCount()).isEqualTo(1);
        TransactionDto dto = blockDto.transactions().get(0);
        assertThat(dto.txHash()).isEqualTo("0xtxhash");
        assertThat(dto.blockNumber()).isEqualTo(16L);
        assertThat(dto.fromAddress()).isEqualTo("0xfrom");
        assertThat(dto.toAddress()).isEqualTo("0xto");
        assertThat(dto.valueEth()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(dto.gas()).isEqualTo(BigInteger.valueOf(21_000));
        assertThat(dto.gasPriceWei()).isEqualTo(BigInteger.valueOf(20_000_000_000L));
        assertThat(dto.maxFeePerGasWei()).isEqualTo(BigInteger.valueOf(30_000_000_000L));
        assertThat(dto.maxPriorityFeePerGasWei()).isEqualTo(BigInteger.valueOf(1_000_000_000L));
        assertThat(dto.nonce()).isEqualTo(BigInteger.valueOf(7));
        assertThat(dto.inputData()).isEqualTo("0x");
        assertThat(dto.transactionType()).isEqualTo("EIP1559");
        assertThat(dto.contractAddress()).isNull();
        assertThat(dto.timestamp()).isEqualTo(Instant.ofEpochSecond(1_700_000_000L));
        assertThat(dto.network()).isEqualTo(NETWORK);
    }

    @Test
    void legacyTransactionWithoutTypeAndFeeFields() {
        EthBlock.TransactionObject transaction = eip1559Transfer();
        transaction.setType(null);
        transaction.setMaxFeePerGas(null);
        transaction.setMaxPriorityFeePerGas(null);

        TransactionDto dto = mapper.toTransactionDto(transaction, 16L, Instant.EPOCH, NETWORK);

        assertThat(dto.transactionType()).isEqualTo("LEGACY");
        assertThat(dto.maxFeePerGasWei()).isNull();
        assertThat(dto.maxPriorityFeePerGasWei()).isNull();
    }

    @Test
    void contractCallUsesToAddressAsContractAddress() {
        EthBlock.TransactionObject transaction = eip1559Transfer();
        transaction.setInput("0xa9059cbb0000");

        TransactionDto dto = mapper.toTransactionDto(transaction, 16L, Instant.EPOCH, NETWORK);

        assertThat(dto.contractAddress()).isEqualTo("0xto");
    }

    @Test
    void contractCreationKeepsNullToAddressAndUsesCreates() {
        EthBlock.TransactionObject transaction = eip1559Transfer();
        transaction.setTo(null);
        transaction.setCreates("0xcreated");
        transaction.setInput("0x600060");

        TransactionDto dto = mapper.toTransactionDto(transaction, 16L, Instant.EPOCH, NETWORK);

        assertThat(dto.toAddress()).isNull();
        assertThat(dto.contractAddress()).isEqualTo("0xcreated");
    }

    private EthBlock.Block block(String numberHex, long epochSeconds, List<EthBlock.TransactionObject> transactions) {
        EthBlock.Block block = new EthBlock.Block();
        block.setNumber(numberHex);
        block.setHash("0xblockhash");
        block.setParentHash("0xparenthash");
        block.setTimestamp("0x" + Long.toHexString(epochSeconds));
        block.setMiner("0xminer");
        block.setGasUsed("0x5208");
        block.setGasLimit("0x1c9c380");
        block.setTransactions(List.copyOf(transactions));
        return block;
    }

    private EthBlock.TransactionObject eip1559Transfer() {
        EthBlock.TransactionObject transaction = new EthBlock.TransactionObject();
        transaction.setHash("0xtxhash");
        transaction.setFrom("0xfrom");
        transaction.setTo("0xto");
        transaction.setValue("0xde0b6b3a7640000");
        transaction.setGas("0x5208");
        transaction.setGasPrice("0x4a817c800");
        transaction.setMaxFeePerGas("0x6fc23ac00");
        transaction.setMaxPriorityFeePerGas("0x3b9aca00");
        transaction.setNonce("0x7");
        transaction.setInput("0x");
        transaction.setType("0x2");
        return transaction;
    }
}
