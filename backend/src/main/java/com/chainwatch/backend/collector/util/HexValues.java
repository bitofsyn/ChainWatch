package com.chainwatch.backend.collector.util;

import java.math.BigInteger;
import org.web3j.utils.Numeric;

/**
 * JSON-RPC hex quantity 파싱 유틸리티. null 입력을 null로 통과시켜
 * 선택 필드(EIP-1559 fee 등)를 안전하게 다룬다.
 */
public final class HexValues {

    private HexValues() {
    }

    public static BigInteger toBigIntegerOrNull(String hexQuantity) {
        if (hexQuantity == null || hexQuantity.isBlank()) {
            return null;
        }
        return Numeric.decodeQuantity(hexQuantity);
    }

    public static BigInteger toBigInteger(String hexQuantity) {
        BigInteger value = toBigIntegerOrNull(hexQuantity);
        if (value == null) {
            throw new IllegalArgumentException("Required hex quantity is missing");
        }
        return value;
    }

    public static long toLong(String hexQuantity) {
        return toBigInteger(hexQuantity).longValueExact();
    }

    public static String toHex(long value) {
        return Numeric.encodeQuantity(BigInteger.valueOf(value));
    }
}
