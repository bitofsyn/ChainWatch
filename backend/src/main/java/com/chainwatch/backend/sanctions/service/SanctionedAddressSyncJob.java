package com.chainwatch.backend.sanctions.service;

import com.chainwatch.backend.sanctions.config.SanctionsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 제재 주소 리스트 주기 동기화 잡. 실패해도 기존 스냅샷으로 탐지가 계속 동작하므로
 * 예외를 삼키고 다음 주기에 재시도한다. 관리자 수동 트리거는 SanctionsController가 담당한다.
 */
@Component
public class SanctionedAddressSyncJob {

    private static final Logger log = LoggerFactory.getLogger(SanctionedAddressSyncJob.class);

    private final SanctionsProperties properties;
    private final SanctionedAddressService sanctionedAddressService;

    public SanctionedAddressSyncJob(
            SanctionsProperties properties,
            SanctionedAddressService sanctionedAddressService
    ) {
        this.properties = properties;
        this.sanctionedAddressService = sanctionedAddressService;
    }

    @Scheduled(
            initialDelayString = "${chainwatch.sanctions.sync-initial-delay-ms:60000}",
            fixedDelayString = "${chainwatch.sanctions.sync-interval-ms:86400000}"
    )
    public void sync() {
        if (!properties.syncEnabled()) {
            return;
        }
        try {
            sanctionedAddressService.syncNow();
        } catch (Exception exception) {
            log.warn("[SANCTIONS_SYNC] periodic sync failed — keeping existing snapshot", exception);
        }
    }
}
