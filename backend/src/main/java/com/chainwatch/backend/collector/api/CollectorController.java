package com.chainwatch.backend.collector.api;

import com.chainwatch.backend.collector.service.CollectorService;
import java.io.IOException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/collector")
public class CollectorController {

    private final CollectorService collectorService;

    public CollectorController(CollectorService collectorService) {
        this.collectorService = collectorService;
    }

    @PostMapping("/blocks/latest")
    public CollectorResponse collectLatestBlock() throws IOException {
        return collectorService.collectLatestBlock();
    }

    @PostMapping("/blocks/{blockNumber}")
    public CollectorResponse collectBlock(@PathVariable long blockNumber) throws IOException {
        return collectorService.collectBlock(blockNumber);
    }
}
