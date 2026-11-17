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

    /**
     * 관심 주소가 거래소 지갑·토큰 컨트랙트처럼 거래가 많은 주소면 트랜잭션마다 고위험 이벤트가 생겨
     * 분석가 큐가 넘친다. 같은 관심 주소는 cooldown 동안 1건으로 묶고, 개별 거래는 지갑 화면에서 본다.
     */
    @Override
    public EventType cooldownEventType() {
        return EventType.WHALE_ACTIVITY;
    }

    @Override
    public String cooldownWalletAddress(Transaction transaction) {
        return matchedAddress(transaction);
    }

    /** 이 트랜잭션에서 관심 주소로 일치한 쪽(발신 우선). 일치가 없으면 null. */
    private String matchedAddress(Transaction transaction) {
        List<String> watchlistAddresses = normalizeAddresses(addressLists.currentAddresses().watchlistAddresses());
        if (watchlistAddresses.contains(normalize(transaction.getFromAddress()))) {
            return transaction.getFromAddress();
        }
        if (watchlistAddresses.contains(normalize(transaction.getToAddress()))) {
            return transaction.getToAddress();
        }
        return null;
    }

    @Override
    public Optional<DetectionCommand> evaluate(Transaction transaction) {
        String walletAddress = matchedAddress(transaction);
        if (walletAddress == null) {
            return Optional.empty();
        }
        boolean fromWatchlist = walletAddress.equals(transaction.getFromAddress());

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("matchedAddress", walletAddress);
        evidence.put("matchedDirection", fromWatchlist ? "FROM" : "TO");
        evidence.put("watchlistReason", WATCHLIST_REASON_CONFIGURED);
        evidence.put("counterpartyAddress", fromWatchlist ? transaction.getToAddress() : transaction.getFromAddress());

        return Optional.of(new DetectionCommand(
                EventType.WHALE_ACTIVITY,
                RiskLevel.HIGH,
                90,
                "관심 주소 " + walletAddress + (fromWatchlist ? "에서 출금" : "로 입금") + " 거래가 발생했습니다",
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
