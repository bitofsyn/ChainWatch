package com.chainwatch.backend.detection.rule;

import com.chainwatch.backend.detection.config.DetectionProperties;
import com.chainwatch.backend.detection.domain.DetectionCommand;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import com.chainwatch.backend.transaction.domain.Transaction;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class WatchlistActivityDetectionRule implements DetectionRule {

    private final DetectionProperties detectionProperties;

    public WatchlistActivityDetectionRule(DetectionProperties detectionProperties) {
        this.detectionProperties = detectionProperties;
    }

    @Override
    public Optional<DetectionCommand> evaluate(Transaction transaction) {
        List<String> watchlistAddresses = normalizeAddresses(detectionProperties.watchlistAddresses());
        String from = normalize(transaction.getFromAddress());
        String to = normalize(transaction.getToAddress());

        boolean fromWatchlist = watchlistAddresses.contains(from);
        boolean toWatchlist = watchlistAddresses.contains(to);

        if (!fromWatchlist && !toWatchlist) {
            return Optional.empty();
        }

        String walletAddress = fromWatchlist ? transaction.getFromAddress() : transaction.getToAddress();
        return Optional.of(new DetectionCommand(
                EventType.WHALE_ACTIVITY,
                RiskLevel.HIGH,
                90,
                "Watchlist wallet activity detected for " + walletAddress,
                walletAddress,
                transaction
        ));
    }

    private List<String> normalizeAddresses(List<String> addresses) {
        return addresses == null ? List.of() : addresses.stream().map(this::normalize).toList();
    }

    private String normalize(String address) {
        return address == null ? "" : address.toLowerCase(Locale.ROOT);
    }
}
