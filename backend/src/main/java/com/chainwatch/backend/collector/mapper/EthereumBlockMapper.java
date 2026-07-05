package com.chainwatch.backend.collector.mapper;

import com.chainwatch.backend.collector.dto.BlockDto;
import com.chainwatch.backend.collector.dto.TransactionDto;
import com.chainwatch.backend.collector.util.HexValues;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.utils.Convert;

/**
 * Web3j 응답 객체를 내부 DTO로 변환한다. Web3j 타입이 Service 계층으로 새어 나가지 않도록
 * 변환 책임을 이 클래스에 한정한다.
 */
@Component
public class EthereumBlockMapper {

    private static final String EMPTY_INPUT = "0x";

    public BlockDto toBlockDto(EthBlock.Block block, String network) {
        Instant blockTimestamp = Instant.ofEpochSecond(block.getTimestamp().longValueExact());
        long blockNumber = block.getNumber().longValueExact();

        List<TransactionDto> transactions = block.getTransactions().stream()
                .map(EthBlock.TransactionResult::get)
                .filter(Transaction.class::isInstance)
                .map(Transaction.class::cast)
                .map(transaction -> toTransactionDto(transaction, blockNumber, blockTimestamp, network))
                .toList();

        return new BlockDto(
                blockNumber,
                block.getHash(),
                block.getParentHash(),
                blockTimestamp,
                block.getMiner(),
                block.getGasUsed(),
                block.getGasLimit(),
                network,
                transactions
        );
    }

    public TransactionDto toTransactionDto(
            Transaction transaction,
            long blockNumber,
            Instant blockTimestamp,
            String network
    ) {
        return new TransactionDto(
                transaction.getHash(),
                blockNumber,
                transaction.getFrom(),
                transaction.getTo(),
                weiToEth(HexValues.toBigIntegerOrNull(transaction.getValueRaw())),
                HexValues.toBigIntegerOrNull(transaction.getGasRaw()),
                HexValues.toBigIntegerOrNull(transaction.getGasPriceRaw()),
                HexValues.toBigIntegerOrNull(transaction.getMaxFeePerGasRaw()),
                HexValues.toBigIntegerOrNull(transaction.getMaxPriorityFeePerGasRaw()),
                HexValues.toBigIntegerOrNull(transaction.getNonceRaw()),
                transaction.getInput(),
                mapTransactionType(transaction.getType()),
                resolveContractAddress(transaction),
                blockTimestamp,
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

    /**
     * 컨트랙트 생성 트랜잭션이면 생성된 주소(노드가 제공하는 경우), 컨트랙트 호출이면 호출 대상 주소를 반환한다.
     */
    private String resolveContractAddress(Transaction transaction) {
        if (transaction.getTo() == null) {
            return transaction.getCreates();
        }
        String input = transaction.getInput();
        if (input != null && !input.isBlank() && !EMPTY_INPUT.equals(input)) {
            return transaction.getTo();
        }
        return null;
    }
}
