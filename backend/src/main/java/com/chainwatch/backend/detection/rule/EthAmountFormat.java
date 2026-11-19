package com.chainwatch.backend.detection.rule;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 탐지 요약 문장에 넣을 ETH 금액 표기. 온체인 금액은 wei 단위까지 정확해 소수점 18자리까지
 * 이어질 수 있으므로(예: 103.162394480000008192), 사람이 읽는 문장에서는 화면과 같이
 * 소수점 6자리까지만 보여준다. 정확한 값은 트랜잭션과 evidence에 그대로 남는다.
 */
final class EthAmountFormat {

    private static final int DISPLAY_SCALE = 6;

    private EthAmountFormat() {
    }

    static String forSummary(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        return amount.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
