package com.chainwatch.backend.detection.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class EthAmountFormatTest {

    @Test
    void roundsWeiPrecisionAmountToSixDecimals() {
        assertThat(EthAmountFormat.forSummary(new BigDecimal("103.162394480000008192"))).isEqualTo("103.162394");
        assertThat(EthAmountFormat.forSummary(new BigDecimal("164.786997524662464"))).isEqualTo("164.786998");
    }

    @Test
    void keepsShortAmountsWithoutTrailingZeros() {
        assertThat(EthAmountFormat.forSummary(new BigDecimal("791.000000000000000000"))).isEqualTo("791");
        assertThat(EthAmountFormat.forSummary(new BigDecimal("100.5"))).isEqualTo("100.5");
        assertThat(EthAmountFormat.forSummary(null)).isEqualTo("0");
    }
}
