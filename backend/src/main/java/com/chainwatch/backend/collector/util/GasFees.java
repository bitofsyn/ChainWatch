package com.chainwatch.backend.collector.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.web3j.utils.Convert;

/**
 * 가스비 추정 유틸리티. 영수증 조회 없이 계산 가능한 상한 추정치를 제공한다:
 * gas limit × (gasPrice 또는 maxFeePerGas).
 */
public final class GasFees {

    private GasFees() {
    }

    public static BigDecimal estimateFeeEth(BigInteger gasPriceWei, BigInteger maxFeePerGasWei, BigInteger gas) {
        BigInteger price = gasPriceWei != null ? gasPriceWei : maxFeePerGasWei;
        if (price == null || gas == null) {
            return BigDecimal.ZERO;
        }
        return Convert.fromWei(new BigDecimal(price.multiply(gas)), Convert.Unit.ETHER);
    }
}
