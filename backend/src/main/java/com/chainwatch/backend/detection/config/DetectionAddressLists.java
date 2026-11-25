package com.chainwatch.backend.detection.config;

import java.util.List;
import java.util.Locale;

/**
 * 룰 엔진이 참조하는 런타임 주소 목록 스냅샷(불변, 소문자 정규화 저장).
 * 기동 시 application.yml({@link DetectionProperties}) 값으로 시작하고,
 * 관리자 API로 변경되면 DB에 저장된 값이 우선한다.
 */
public record DetectionAddressLists(
        List<String> watchlistAddresses,
        List<String> exchangeAddresses
) {

    public static DetectionAddressLists fromProperties(DetectionProperties properties) {
        return new DetectionAddressLists(
                lowercase(properties.watchlistAddresses()),
                lowercase(properties.exchangeAddresses())
        );
    }

    /** 등록된 거래소 주소(자사 입출금 지갑)인지. 대소문자를 구분하지 않는다. */
    public boolean isExchangeAddress(String address) {
        return address != null
                && exchangeAddresses != null
                && exchangeAddresses.stream().anyMatch(address::equalsIgnoreCase);
    }

    private static List<String> lowercase(List<String> addresses) {
        if (addresses == null) {
            return List.of();
        }
        return addresses.stream()
                .filter(address -> address != null && !address.isBlank())
                .map(address -> address.trim().toLowerCase(Locale.ROOT))
                .toList();
    }
}
