package com.chainwatch.backend.sanctions.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OFAC SDN 제재 주소 리스트 동기화 정책.
 * 기본 소스는 OFAC SDN 원문에서 암호화폐 주소를 CI로 추출·공개하는 목록이다.
 * 실서비스에서는 내부 미러 또는 SDN 원문(XML) 직접 파싱으로 대체할 것.
 * 동기화가 꺼져 있어도 DB에 저장된 기존 제재 주소로 탐지 룰은 계속 동작한다.
 */
@ConfigurationProperties(prefix = "chainwatch.sanctions")
public record SanctionsProperties(
        boolean syncEnabled,
        String sourceUrl
) {

    private static final String DEFAULT_SOURCE_URL =
            "https://raw.githubusercontent.com/0xB10C/ofac-sanctioned-digital-currency-addresses"
                    + "/lists/sanctioned_addresses_ETH.txt";

    public SanctionsProperties {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            sourceUrl = DEFAULT_SOURCE_URL;
        }
    }
}
