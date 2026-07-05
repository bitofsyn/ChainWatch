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
