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

    private static final int MAX_COUNTERPARTIES = 50;

    private final DetectionEventRepository detectionEventRepository;
    private final com.chainwatch.backend.transaction.repository.TransactionRepository transactionRepository;

    public WalletSummaryController(
            DetectionEventRepository detectionEventRepository,
            com.chainwatch.backend.transaction.repository.TransactionRepository transactionRepository
    ) {
        this.detectionEventRepository = detectionEventRepository;
        this.transactionRepository = transactionRepository;
    }

    public record Counterparty(String address, long txCount, java.math.BigDecimal totalAmount, Instant lastAt) {
    }

    /** 수집된 거래 기준으로 이 지갑이 자금을 보낸 곳과 받은 곳. 수집하지 않은 구간의 거래는 포함되지 않는다. */
    public record CounterpartiesResponse(
            String address,
            long outgoingTxCount,
            long incomingTxCount,
            List<Counterparty> recipients,
            List<Counterparty> senders
    ) {
    }

    @GetMapping("/{address}/counterparties")
    public CounterpartiesResponse getCounterparties(
            @PathVariable String address,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int limit
    ) {
        String normalized = address.toLowerCase(java.util.Locale.ROOT);
        var top = org.springframework.data.domain.PageRequest.of(0, Math.min(Math.max(limit, 1), MAX_COUNTERPARTIES));
        return new CounterpartiesResponse(
                normalized,
                transactionRepository.countByFromAddress(normalized),
                transactionRepository.countByToAddress(normalized),
                toCounterparties(transactionRepository.summarizeRecipients(normalized, top)),
                toCounterparties(transactionRepository.summarizeSenders(normalized, top))
        );
    }

    private static List<Counterparty> toCounterparties(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new Counterparty(
                        String.valueOf(row[0]),
                        ((Number) row[1]).longValue(),
                        row[2] != null ? new java.math.BigDecimal(row[2].toString()) : java.math.BigDecimal.ZERO,
                        (Instant) row[3]))
                .toList();
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
