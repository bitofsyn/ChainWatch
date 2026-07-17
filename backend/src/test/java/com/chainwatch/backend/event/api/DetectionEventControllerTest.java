package com.chainwatch.backend.event.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainwatch.backend.event.domain.DetectionEvent;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import com.chainwatch.backend.event.repository.DetectionEventRepository;
import com.chainwatch.backend.transaction.domain.Transaction;
import com.chainwatch.backend.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the detection-event read endpoints serialize the LAZY {@code transaction}
 * association without a surrounding transaction (open-in-view is disabled). The test is
 * intentionally not wrapped in {@code @Transactional} so it reproduces the production
 * request lifecycle and would fail with LazyInitializationException without the
 * {@code @EntityGraph} fetch on the repository.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DetectionEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private DetectionEventRepository detectionEventRepository;

    @AfterEach
    void cleanUp() {
        detectionEventRepository.deleteAll();
        transactionRepository.deleteAll();
    }

    @Test
    void getEventsExposesLinkedTransactionHash() throws Exception {
        seedEvent("0xhash-list");

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].txHash").value("0xhash-list"));
    }

    @Test
    void getEventDetailExposesLinkedTransaction() throws Exception {
        DetectionEvent event = seedEvent("0xhash-detail");

        mockMvc.perform(get("/api/events/" + event.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.txHash").value("0xhash-detail"))
                .andExpect(jsonPath("$.transactionId").value(event.getTransaction().getId()));
    }

    @Test
    void assigneeFilterMatchesCaseInsensitively() throws Exception {
        seedEventWithAssignee("0xhash-alice", "Alice");
        seedEventWithAssignee("0xhash-bob", "bob");

        mockMvc.perform(get("/api/events").param("assignee", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].assignee").value("Alice"));
    }

    @Test
    void unassignedFilterReturnsOnlyEventsWithoutAssignee() throws Exception {
        seedEventWithAssignee("0xhash-assigned", "carol");
        seedEvent("0xhash-unassigned");

        mockMvc.perform(get("/api/events").param("unassigned", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].txHash").value("0xhash-unassigned"));
    }

    @Test
    void unassignedFilterTakesPrecedenceOverAssignee() throws Exception {
        seedEventWithAssignee("0xhash-assigned2", "dave");
        seedEvent("0xhash-unassigned2");

        mockMvc.perform(get("/api/events").param("unassigned", "true").param("assignee", "dave"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].assignee").doesNotExist());
    }

    @Test
    void eventInheritsNetworkFromTransactionAndIsExposed() throws Exception {
        seedEventOnNetwork("0xhash-polygon", "polygon-mainnet");

        mockMvc.perform(get("/api/events").param("wallet", "0xfrom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].network").value("polygon-mainnet"));
    }

    @Test
    void networkFilterMatchesCaseInsensitively() throws Exception {
        seedEventOnNetwork("0xhash-eth", "ethereum-mainnet");
        seedEventOnNetwork("0xhash-poly", "polygon-mainnet");

        mockMvc.perform(get("/api/events").param("network", "POLYGON-MAINNET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].network").value("polygon-mainnet"));
    }

    @Test
    void defaultNetworkFilterIncludesLegacyNullRows() throws Exception {
        // 레거시 경로(8-arg 생성자)는 network를 기본 체인으로 채운다
        seedEvent("0xhash-legacy");
        seedEventOnNetwork("0xhash-poly2", "polygon-mainnet");

        mockMvc.perform(get("/api/events").param("network", "ethereum-mainnet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].network").value("ethereum-mainnet"));
    }

    private DetectionEvent seedEventOnNetwork(String txHash, String network) {
        Transaction transaction = transactionRepository.save(new Transaction(
                txHash, "0xfrom", "0xto", new BigDecimal("1.5"), new BigDecimal("0.01"),
                100L, Instant.now(), null, network));
        return detectionEventRepository.save(new DetectionEvent(
                EventType.LARGE_TRANSFER, RiskLevel.HIGH, 90,
                "Large transfer detected", "0xfrom", Instant.now(), transaction));
    }

    private DetectionEvent seedEventWithAssignee(String txHash, String assignee) {
        DetectionEvent event = seedEvent(txHash);
        event.applyStatusChange(
                com.chainwatch.backend.event.domain.EventStatus.INVESTIGATING, assignee, null, null, null);
        return detectionEventRepository.save(event);
    }

    private DetectionEvent seedEvent(String txHash) {
        Transaction transaction = transactionRepository.save(new Transaction(
                txHash,
                "0xfrom",
                "0xto",
                new BigDecimal("1.5"),
                new BigDecimal("0.01"),
                100L,
                Instant.now(),
                null
        ));
        return detectionEventRepository.save(new DetectionEvent(
                EventType.LARGE_TRANSFER,
                RiskLevel.HIGH,
                90,
                "Large transfer detected",
                "0xfrom",
                Instant.now(),
                transaction
        ));
    }
}
