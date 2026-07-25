package com.chainwatch.backend.detection.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.chainwatch.backend.detection.domain.DetectionCommand;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import com.chainwatch.backend.sanctions.service.SanctionedAddressChecker;
import com.chainwatch.backend.transaction.domain.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 제재 주소 룰: 송신/수신 어느 쪽이든 제재 주소가 관여하면 CRITICAL/100으로 발화하고 evidence를 남긴다. */
class SanctionedAddressDetectionRuleTest {

    private static final String SANCTIONED = "0x7f367cc41522ce07553e823bf3be79a889debe1b";

    private final SanctionedAddressChecker checker =
            address -> address != null && Set.of(SANCTIONED).contains(address.toLowerCase(Locale.ROOT));

    private final SanctionedAddressDetectionRule rule = new SanctionedAddressDetectionRule(checker);

    @Test
    void firesCriticalWhenSenderIsSanctioned() {
        Transaction transaction = transaction(SANCTIONED, "0xto");

        DetectionCommand command = rule.evaluate(transaction).orElseThrow();

        assertThat(command.eventType()).isEqualTo(EventType.SANCTIONED_ADDRESS);
        assertThat(command.riskLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(command.riskScore()).isEqualTo(100);
        assertThat(command.walletAddress()).isEqualTo(SANCTIONED);
        assertThat(command.ruleName()).isEqualTo("sanctioned-address");
        assertThat(command.evidence()).containsEntry("matchedAddress", SANCTIONED);
        assertThat(command.evidence()).containsEntry("matchedDirection", "FROM");
        assertThat(command.evidence()).containsEntry("sanctionsSource", "OFAC_SDN");
        assertThat(command.evidence()).containsEntry("counterpartyAddress", "0xto");
    }

    @Test
    void firesWhenRecipientIsSanctionedRegardlessOfCase() {
        Transaction transaction = transaction("0xfrom", SANCTIONED.toUpperCase(Locale.ROOT).replace("0X", "0x"));

        DetectionCommand command = rule.evaluate(transaction).orElseThrow();

        assertThat(command.evidence()).containsEntry("matchedDirection", "TO");
        assertThat(command.evidence()).containsEntry("counterpartyAddress", "0xfrom");
    }

    @Test
    void staysQuietWhenNoSanctionedAddressInvolved() {
        assertThat(rule.evaluate(transaction("0xfrom", "0xto"))).isEmpty();
    }

    private Transaction transaction(String from, String to) {
        return new Transaction(
                "0xhash", from, to, new BigDecimal("1"), BigDecimal.ZERO, 1L, Instant.now(), null);
    }
}
