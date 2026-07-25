package com.chainwatch.backend.detection.rule;

import com.chainwatch.backend.detection.domain.DetectionCommand;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import com.chainwatch.backend.sanctions.service.SanctionedAddressChecker;
import com.chainwatch.backend.sanctions.service.SanctionedAddressService;
import com.chainwatch.backend.transaction.domain.Transaction;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * OFAC SDN 제재 대상 주소가 송신 또는 수신으로 관여한 트랜잭션을 탐지한다.
 * 제재 주소 관여는 금액·패턴과 무관하게 그 자체로 컴플라이언스 대응 대상이므로
 * 항상 CRITICAL/100으로 발화한다.
 */
@Component
public class SanctionedAddressDetectionRule implements DetectionRule {

    static final String RULE_NAME = "sanctioned-address";
    static final String RULE_VERSION = "1.0";

    private final SanctionedAddressChecker sanctionedAddresses;

    public SanctionedAddressDetectionRule(SanctionedAddressChecker sanctionedAddresses) {
        this.sanctionedAddresses = sanctionedAddresses;
    }

    @Override
    public Optional<DetectionCommand> evaluate(Transaction transaction) {
        boolean fromSanctioned = sanctionedAddresses.isSanctioned(transaction.getFromAddress());
        boolean toSanctioned = sanctionedAddresses.isSanctioned(transaction.getToAddress());

        if (!fromSanctioned && !toSanctioned) {
            return Optional.empty();
        }

        String walletAddress = fromSanctioned ? transaction.getFromAddress() : transaction.getToAddress();

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("matchedAddress", walletAddress);
        evidence.put("matchedDirection", fromSanctioned ? "FROM" : "TO");
        evidence.put("sanctionsSource", SanctionedAddressService.SOURCE_OFAC_SDN);
        evidence.put("counterpartyAddress",
                fromSanctioned ? transaction.getToAddress() : transaction.getFromAddress());

        return Optional.of(new DetectionCommand(
                EventType.SANCTIONED_ADDRESS,
                RiskLevel.CRITICAL,
                100,
                "Sanctioned (OFAC SDN) address activity detected for " + walletAddress,
                walletAddress,
                transaction,
                RULE_NAME,
                RULE_VERSION,
                evidence
        ));
    }
}
