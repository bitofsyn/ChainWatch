package com.chainwatch.backend.detection.config;

import java.util.List;

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
                properties.watchlistAddresses() != null ? List.copyOf(properties.watchlistAddresses()) : List.of(),
                properties.exchangeAddresses() != null ? List.copyOf(properties.exchangeAddresses()) : List.of()
        );
    }
}
