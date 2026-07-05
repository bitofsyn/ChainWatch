package com.chainwatch.backend.wallet.api;

import com.chainwatch.backend.event.repository.DetectionEventRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallets")
public class WalletSummaryController {

    private final DetectionEventRepository detectionEventRepository;

    public WalletSummaryController(DetectionEventRepository detectionEventRepository) {
        this.detectionEventRepository = detectionEventRepository;
    }

    @GetMapping("/{address}")
    public WalletSummaryResponse getWalletSummary(@PathVariable String address) {
        Object[] summary = detectionEventRepository.summarizeWallet(address).get(0);

        long eventCount = summary[0] != null ? ((Number) summary[0]).longValue() : 0;
        int maxRiskScore = summary[1] != null ? ((Number) summary[1]).intValue() : 0;
        Instant firstDetectedAt = (Instant) summary[2];
        Instant lastDetectedAt = (Instant) summary[3];

        List<WalletSummaryResponse.KeyCount> eventTypeCounts = toKeyCounts(
                detectionEventRepository.countGroupByEventTypeForWallet(address));
        List<WalletSummaryResponse.KeyCount> riskLevelCounts = toKeyCounts(
                detectionEventRepository.countGroupByRiskLevelForWallet(address));

        return new WalletSummaryResponse(
                address,
                eventCount,
                maxRiskScore,
                firstDetectedAt,
                lastDetectedAt,
                eventTypeCounts,
                riskLevelCounts
        );
    }

    private static List<WalletSummaryResponse.KeyCount> toKeyCounts(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new WalletSummaryResponse.KeyCount(String.valueOf(row[0]), (Long) row[1]))
                .toList();
    }
}
