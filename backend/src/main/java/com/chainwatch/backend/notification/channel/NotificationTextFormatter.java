package com.chainwatch.backend.notification.channel;

import com.chainwatch.backend.notification.domain.NotificationMessage;

public final class NotificationTextFormatter {

    private static final String UNKNOWN = "N/A";

    private NotificationTextFormatter() {
    }

    public static String format(NotificationMessage message) {
        return """
                🚨 [ChainWatch] 이상거래 탐지
                유형: %s | 위험 등급: %s (점수 %d)
                지갑: %s
                트랜잭션: %s
                요약: %s""".formatted(
                orUnknown(message.eventType()),
                orUnknown(message.riskLevel()),
                message.riskScore(),
                orUnknown(message.walletAddress()),
                orUnknown(message.txHash()),
                orUnknown(message.summary())
        );
    }

    private static String orUnknown(String value) {
        return value != null && !value.isBlank() ? value : UNKNOWN;
    }
}
