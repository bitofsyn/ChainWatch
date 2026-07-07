package com.chainwatch.backend.collector.api;

import com.chainwatch.backend.audit.service.AuditLogService;
import com.chainwatch.backend.collector.service.BlockCollectionService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/collector")
public class CollectorController {

    private static final String AUDIT_TARGET_TYPE_COLLECTOR = "COLLECTOR";

    private final BlockCollectionService blockCollectionService;
    private final AuditLogService auditLogService;

    public CollectorController(BlockCollectionService blockCollectionService, AuditLogService auditLogService) {
        this.blockCollectionService = blockCollectionService;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/blocks/latest")
    public CollectorResponse collectLatestBlock() {
        CollectorResponse response = blockCollectionService.collectLatestBlock();
        auditLogService.record("COLLECTOR_COLLECT_LATEST", AUDIT_TARGET_TYPE_COLLECTOR, "latest",
                "Manual latest block collection triggered");
        return response;
    }

    @PostMapping("/blocks/{blockNumber}")
    public CollectorResponse collectBlock(@PathVariable long blockNumber) {
        CollectorResponse response = blockCollectionService.collectBlock(blockNumber);
        auditLogService.record("COLLECTOR_COLLECT_BLOCK", AUDIT_TARGET_TYPE_COLLECTOR, String.valueOf(blockNumber),
                "Manual block collection/reprocess triggered for block " + blockNumber);
        return response;
    }

    @GetMapping("/state")
    public Map<String, Long> collectorState() {
        return Map.of("lastCollectedBlock", blockCollectionService.lastCollectedBlockNumber());
    }
}
