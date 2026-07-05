package com.chainwatch.backend.collector.api;

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

    private final BlockCollectionService blockCollectionService;

    public CollectorController(BlockCollectionService blockCollectionService) {
        this.blockCollectionService = blockCollectionService;
    }

    @PostMapping("/blocks/latest")
    public CollectorResponse collectLatestBlock() {
        return blockCollectionService.collectLatestBlock();
    }

    @PostMapping("/blocks/{blockNumber}")
    public CollectorResponse collectBlock(@PathVariable long blockNumber) {
        return blockCollectionService.collectBlock(blockNumber);
    }

    @GetMapping("/state")
    public Map<String, Long> collectorState() {
        return Map.of("lastCollectedBlock", blockCollectionService.lastCollectedBlockNumber());
    }
}
