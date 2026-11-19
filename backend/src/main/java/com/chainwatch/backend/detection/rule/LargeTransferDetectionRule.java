package com.chainwatch.backend.detection.rule;

import com.chainwatch.backend.detection.config.DetectionThresholdsProvider;
import com.chainwatch.backend.detection.domain.DetectionCommand;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import com.chainwatch.backend.transaction.domain.Transaction;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class LargeTransferDetectionRule implements DetectionRule {

    private static final String CONTRACT_CREATION_MARKER = "CONTRACT_CREATION";
    static final String RULE_NAME = "large-transfer";
    static final String RULE_VERSION = "1.0";

    private final DetectionThresholdsProvider thresholds;

    public LargeTransferDetectionRule(DetectionThresholdsProvider thresholds) {
        this.thresholds = thresholds;
    }

    @Override
    public Optional<DetectionCommand> evaluate(Transaction transaction) {
        BigDecimal threshold = thresholds.current().largeTransferThresholdEth();
        if (threshold == null || transaction.getAmount().compareTo(threshold) < 0) {
            return Optional.empty();
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("thresholdEth", threshold);
        evidence.put("observedAmountEth", transaction.getAmount());
        evidence.put("fromAddress", transaction.getFromAddress());
        evidence.put("toAddress", transaction.getToAddress());

        return Optional.of(new DetectionCommand(
                EventType.LARGE_TRANSFER,
                RiskLevel.HIGH,
                85,
                summary(transaction),
                transaction.getToAddress(),
                transaction,
                RULE_NAME,
                RULE_VERSION,
                evidence
        ));
    }

    /** 컨트랙트 생성 거래는 받는 주소가 없어 수집기가 표식 문자열을 넣는다. 그대로 주소처럼 보여주지 않는다. */
    private static String summary(Transaction transaction) {
        String amount = EthAmountFormat.forSummary(transaction.getAmount());
        if (CONTRACT_CREATION_MARKER.equals(transaction.getToAddress())) {
            return "대규모 이체: " + amount + " ETH가 컨트랙트 생성 거래로 전송되었습니다";
        }
        return "대규모 이체: " + amount + " ETH가 " + transaction.getToAddress() + " 주소로 이동했습니다";
    }
}
