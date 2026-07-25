package com.chainwatch.backend.sanctions.api;

import com.chainwatch.backend.sanctions.config.SanctionsProperties;
import com.chainwatch.backend.sanctions.service.SanctionedAddressService;
import com.chainwatch.backend.sanctions.service.SanctionedAddressService.SyncSummary;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 제재 주소 동기화 상태 조회·수동 트리거 API.
 * POST /api/sanctions/sync는 SecurityConfig ADMIN_CONTROL_PATHS로 ADMIN 전용이다.
 */
@RestController
@RequestMapping("/api/sanctions")
public class SanctionsController {

    private final SanctionedAddressService sanctionedAddressService;
    private final SanctionsProperties properties;

    public SanctionsController(
            SanctionedAddressService sanctionedAddressService,
            SanctionsProperties properties
    ) {
        this.sanctionedAddressService = sanctionedAddressService;
        this.properties = properties;
    }

    public record SanctionsStatusResponse(
            int activeCount,
            boolean syncEnabled,
            String sourceUrl,
            Instant lastSyncAt,
            Integer lastAdded,
            Integer lastDelisted
    ) {
    }

    public record SanctionsSyncResponse(int added, int delisted, int activeTotal, Instant syncedAt) {
    }

    @GetMapping
    public SanctionsStatusResponse status() {
        SyncSummary last = sanctionedAddressService.lastSyncSummary();
        return new SanctionsStatusResponse(
                sanctionedAddressService.activeCount(),
                properties.syncEnabled(),
                properties.sourceUrl(),
                last != null ? last.syncedAt() : null,
                last != null ? last.added() : null,
                last != null ? last.delisted() : null
        );
    }

    @PostMapping("/sync")
    public SanctionsSyncResponse sync() {
        SyncSummary summary = sanctionedAddressService.syncNow();
        return new SanctionsSyncResponse(
                summary.added(), summary.delisted(), summary.activeTotal(), summary.syncedAt());
    }
}
