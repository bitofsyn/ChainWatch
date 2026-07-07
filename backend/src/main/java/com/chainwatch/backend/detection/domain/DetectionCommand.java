package com.chainwatch.backend.detection.domain;

import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import com.chainwatch.backend.transaction.domain.Transaction;
import java.util.Map;

/**
 * 룰 평가 결과. evidence는 "왜 발화했는가"를 구조화한 값(임계값, 관측값, 매칭 주소 등)으로,
 * DetectionService가 rule/ruleVersion과 함께 JSON으로 직렬화해 DetectionEvent에 저장한다.
 */
public record DetectionCommand(
        EventType eventType,
        RiskLevel riskLevel,
        int riskScore,
        String summary,
        String walletAddress,
        Transaction transaction,
        String ruleName,
        String ruleVersion,
        Map<String, Object> evidence
) {
}
