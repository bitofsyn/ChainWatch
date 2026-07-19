package com.chainwatch.backend.detection.rule;

import com.chainwatch.backend.detection.config.DetectionAddressListsProvider;
import com.chainwatch.backend.detection.domain.DetectionCommand;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import com.chainwatch.backend.transaction.domain.Transaction;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class WatchlistActivityDetectionRule implements DetectionRule {

    static final String RULE_NAME = "watchlist-activity";
    static final String RULE_VERSION = "1.0";
    /** watchlist가 주소 목록만 갖는 현재 구조에서의 고정 사유. 라벨/제재 사유 연동 시 세분화한다. */
    static final String WATCHLIST_REASON_CONFIGURED = "configured-watchlist-address";

    private final DetectionAddressListsProvider addressLists;

    public WatchlistActivityDetectionRule(DetectionAddressListsProvider addressLists) {
        this.addressLists = addressLists;
    }

    @Override
    public Optional<DetectionCommand> evaluate(Transaction transaction) {
        List<String> watchlistAddresses = normalizeAddresses(addressLists.currentAddresses().watchlistAddresses());
        String from = normalize(transaction.getFromAddress());
        String to = normalize(transaction.getToAddress());

        boolean fromWatchlist = watchlistAddresses.contains(from);
        boolean toWatchlist = watchlistAddresses.contains(to);

        if (!fromWatchlist && !toWatchlist) {
            return Optional.empty();
        }

        String walletAddress = fromWatchlist ? transaction.getFromAddress() : transaction.getToAddress();

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("matchedAddress", walletAddress);
        evidence.put("matchedDirection", fromWatchlist ? "FROM" : "TO");
        evidence.put("watchlistReason", WATCHLIST_REASON_CONFIGURED);
        evidence.put("counterpartyAddress", fromWatchlist ? transaction.getToAddress() : transaction.getFromAddress());

        return Optional.of(new DetectionCommand(
                EventType.WHALE_ACTIVITY,
                RiskLevel.HIGH,
                90,
                "Watchlist wallet activity detected for " + walletAddress,
                walletAddress,
                transaction,
                RULE_NAME,
                RULE_VERSION,
                evidence
        ));
    }

    private List<String> normalizeAddresses(List<String> addresses) {
        return addresses == null ? List.of() : addresses.stream().map(this::normalize).toList();
    }

    private String normalize(String address) {
        return address == null ? "" : address.toLowerCase(Locale.ROOT);
    }
}
