package com.chainwatch.backend.detection.rule;

import com.chainwatch.backend.detection.config.DetectionProperties;
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

    private final DetectionProperties detectionProperties;
    private final TransactionRepository transactionRepository;

    public FanOutDetectionRule(
            DetectionProperties detectionProperties,
            TransactionRepository transactionRepository
    ) {
        this.detectionProperties = detectionProperties;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public Optional<DetectionCommand> evaluate(Transaction transaction) {
        if (detectionProperties.fanOutThresholdRecipients() <= 1) {
            return Optional.empty();
        }

        Instant windowStart = transaction.getTimestamp()
                .minus(detectionProperties.fanOutWindowMinutes(), ChronoUnit.MINUTES);

        long distinctRecipients = transactionRepository.countDistinctRecipientsFromAddress(
                transaction.getFromAddress(),
                windowStart
        );

        if (distinctRecipients < detectionProperties.fanOutThresholdRecipients()) {
            return Optional.empty();
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("windowMinutes", detectionProperties.fanOutWindowMinutes());
        evidence.put("thresholdRecipients", detectionProperties.fanOutThresholdRecipients());
        evidence.put("observedDistinctRecipients", distinctRecipients);
        evidence.put("windowStart", windowStart.toString());
        evidence.put("fromAddress", transaction.getFromAddress());

        return Optional.of(new DetectionCommand(
                EventType.FAN_OUT,
                RiskLevel.HIGH,
                78,
                "Fan-out pattern detected: " + transaction.getFromAddress()
                        + " sent to " + distinctRecipients + " distinct addresses within "
                        + detectionProperties.fanOutWindowMinutes() + " minutes",
                transaction.getFromAddress(),
                transaction,
                RULE_NAME,
                RULE_VERSION,
                evidence
        ));
    }
}
