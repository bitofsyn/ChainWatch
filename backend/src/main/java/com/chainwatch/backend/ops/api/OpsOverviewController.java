package com.chainwatch.backend.ops.api;

import com.chainwatch.backend.ops.service.OpsOverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영 대시보드 집계 API. range/bucket은 서비스에서 allowlist로 검증되며
 * 허용되지 않은 조합은 400(BAD_REQUEST)으로 거부된다.
 */
@RestController
@RequestMapping("/api/ops/overview")
public class OpsOverviewController {

    private final OpsOverviewService opsOverviewService;

    public OpsOverviewController(OpsOverviewService opsOverviewService) {
        this.opsOverviewService = opsOverviewService;
    }

    @GetMapping
    public OpsOverviewResponse getOverview(
            @RequestParam(defaultValue = "24h") String range,
            @RequestParam(defaultValue = "1h") String bucket
    ) {
        return opsOverviewService.overview(range, bucket);
    }
}
