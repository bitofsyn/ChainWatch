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

/**
 * 자금 분산(fan-out) 그래프 패턴 탐지 룰.
 *
 * <p>단순 트랜잭션 빈도(RapidTransfer)와 달리 트랜잭션 그래프의 <b>out-degree</b>를 본다.
 * 한 발신 지갑이 짧은 시간창 안에 임계값 이상의 <b>서로 다른</b> 수신 주소로 자금을 보내면,
 * peeling chain(단계적 소액 분산)이나 자금 스플리팅/세탁 초기 단계일 수 있어 발화한다.
 * 같은 상대에게 반복 송금하는 경우는 out-degree가 낮아 이 룰로는 잡히지 않는다(RapidTransfer의 영역).
 */
@Component
public class FanOutDetectionRule implements DetectionRule {

    static final String RULE_NAME = "fan-out";
    static final String RULE_VERSION = "1.0";

    private final DetectionThresholdsProvider thresholds;
    private final DetectionAddressListsProvider addressLists;
    private final TransactionRepository transactionRepository;

    public FanOutDetectionRule(
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
        return EventType.FAN_OUT;
    }

    @Override
    public Optional<DetectionCommand> evaluate(Transaction transaction) {
        // 자사 입출금 지갑(등록된 거래소 주소)은 고객 출금을 한꺼번에 보내므로 이 패턴이 정상이다.
        // 자사 지갑의 대량 입출금은 자사 지갑 입출금 룰(EXCHANGE_FLOW)이 따로 본다.
        if (addressLists.currentAddresses().isExchangeAddress(transaction.getFromAddress())) {
            return Optional.empty();
        }
        DetectionThresholds current = thresholds.current();
        if (current.fanOutThresholdRecipients() <= 1) {
            return Optional.empty();
        }

        Instant windowStart = transaction.getTimestamp()
                .minus(current.fanOutWindowMinutes(), ChronoUnit.MINUTES);

        long distinctRecipients = transactionRepository.countDistinctRecipientsFromAddress(
                transaction.getFromAddress(),
                windowStart
        );

        if (distinctRecipients < current.fanOutThresholdRecipients()) {
            return Optional.empty();
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("windowMinutes", current.fanOutWindowMinutes());
        evidence.put("thresholdRecipients", current.fanOutThresholdRecipients());
        evidence.put("observedDistinctRecipients", distinctRecipients);
        evidence.put("windowStart", windowStart.toString());
        evidence.put("fromAddress", transaction.getFromAddress());

        return Optional.of(new DetectionCommand(
                EventType.FAN_OUT,
                RiskLevel.HIGH,
                78,
                "자금 분산: " + transaction.getFromAddress() + " 주소가 "
                        + current.fanOutWindowMinutes() + "분 안에 서로 다른 주소 " + distinctRecipients + "곳으로 송금했습니다",
                transaction.getFromAddress(),
                transaction,
                RULE_NAME,
                RULE_VERSION,
                evidence
        ));
    }
}
