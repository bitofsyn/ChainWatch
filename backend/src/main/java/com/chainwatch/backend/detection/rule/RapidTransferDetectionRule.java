package com.chainwatch.backend.detection.rule;

import com.chainwatch.backend.detection.config.DetectionAddressListsProvider;
import com.chainwatch.backend.detection.config.DetectionThresholds;
import com.chainwatch.backend.detection.config.DetectionThresholdsProvider;
import com.chainwatch.backend.detection.domain.DetectionCommand;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import com.chainwatch.backend.transaction.domain.Transaction;
import com.chainwatch.backend.transaction.repository.TransactionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RapidTransferDetectionRule implements DetectionRule {

    static final String RULE_NAME = "rapid-transfer";
    static final String RULE_VERSION = "1.0";

    private final DetectionThresholdsProvider thresholds;
    private final DetectionAddressListsProvider addressLists;
    private final TransactionRepository transactionRepository;

    public RapidTransferDetectionRule(
            DetectionThresholdsProvider thresholds,
            DetectionAddressListsProvider addressLists,
            TransactionRepository transactionRepository
    ) {
        this.thresholds = thresholds;
        this.addressLists = addressLists;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public EventType cooldownEventType() {
        // walletAddress = fromAddress 계약 (DetectionRule.cooldownEventType 문서 참조)
        return EventType.RAPID_TRANSFER;
    }

    @Override
    public Optional<DetectionCommand> evaluate(Transaction transaction) {
        // 자사 입출금 지갑(등록된 거래소 주소)은 고객 출금을 한꺼번에 보내므로 이 패턴이 정상이다.
        // 자사 지갑의 대량 입출금은 자사 지갑 입출금 룰(EXCHANGE_FLOW)이 따로 본다.
        if (addressLists.currentAddresses().isExchangeAddress(transaction.getFromAddress())) {
            return Optional.empty();
        }
        DetectionThresholds current = thresholds.current();
        if (current.rapidTransferThresholdCount() <= 1) {
            return Optional.empty();
        }

        Instant thresholdTime = transaction.getTimestamp()
                .minus(current.rapidTransferWindowMinutes(), ChronoUnit.MINUTES);

        long recentTransferCount = transactionRepository.countRecentTransfersFromAddress(
                transaction.getFromAddress(),
                thresholdTime
        );

        if (recentTransferCount < current.rapidTransferThresholdCount()) {
            return Optional.empty();
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("windowMinutes", current.rapidTransferWindowMinutes());
        evidence.put("thresholdCount", current.rapidTransferThresholdCount());
        evidence.put("observedTransferCount", recentTransferCount);
        evidence.put("windowStart", thresholdTime.toString());
        evidence.put("fromAddress", transaction.getFromAddress());

        return Optional.of(new DetectionCommand(
                EventType.RAPID_TRANSFER,
                RiskLevel.MEDIUM,
                72,
                "반복 이체: " + transaction.getFromAddress() + " 주소가 "
                        + current.rapidTransferWindowMinutes() + "분 안에 " + recentTransferCount + "회 이체했습니다",
                transaction.getFromAddress(),
                transaction,
                RULE_NAME,
                RULE_VERSION,
                evidence
        ));
    }
}
