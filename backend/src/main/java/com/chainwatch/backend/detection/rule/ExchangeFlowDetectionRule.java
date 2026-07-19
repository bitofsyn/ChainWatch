package com.chainwatch.backend.detection.rule;

import com.chainwatch.backend.detection.config.DetectionAddressListsProvider;
import com.chainwatch.backend.detection.config.DetectionThresholdsProvider;
import com.chainwatch.backend.detection.domain.DetectionCommand;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import com.chainwatch.backend.transaction.domain.Transaction;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ExchangeFlowDetectionRule implements DetectionRule {

    static final String RULE_NAME = "exchange-flow";
    static final String RULE_VERSION = "1.0";

    private final DetectionAddressListsProvider addressLists;
    private final DetectionThresholdsProvider thresholds;

    public ExchangeFlowDetectionRule(
            DetectionAddressListsProvider addressLists,
            DetectionThresholdsProvider thresholds
    ) {
        this.addressLists = addressLists;
        this.thresholds = thresholds;
    }

    @Override
    public Optional<DetectionCommand> evaluate(Transaction transaction) {
        BigDecimal threshold = thresholds.current().exchangeFlowThresholdEth();
        if (threshold == null || transaction.getAmount().compareTo(threshold) < 0) {
            return Optional.empty();
        }

        List<String> exchangeAddresses = normalizeAddresses(addressLists.currentAddresses().exchangeAddresses());
        String from = normalize(transaction.getFromAddress());
        String to = normalize(transaction.getToAddress());
        boolean fromExchange = exchangeAddresses.contains(from);
        boolean toExchange = exchangeAddresses.contains(to);

        if (!fromExchange && !toExchange) {
            return Optional.empty();
        }

        String direction = toExchange ? "into exchange" : "out of exchange";
        String walletAddress = toExchange ? transaction.getToAddress() : transaction.getFromAddress();
        int riskScore = toExchange ? 88 : 78;

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("thresholdEth", threshold);
        evidence.put("observedAmountEth", transaction.getAmount());
        evidence.put("direction", toExchange ? "INBOUND" : "OUTBOUND");
        evidence.put("matchedExchangeAddress", toExchange ? transaction.getToAddress() : transaction.getFromAddress());
        evidence.put("counterpartyAddress", toExchange ? transaction.getFromAddress() : transaction.getToAddress());

        return Optional.of(new DetectionCommand(
                EventType.EXCHANGE_FLOW,
                RiskLevel.HIGH,
                riskScore,
                "Exchange flow detected: " + transaction.getAmount() + " ETH moved " + direction,
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
