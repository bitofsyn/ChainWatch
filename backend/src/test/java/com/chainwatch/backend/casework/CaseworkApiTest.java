package com.chainwatch.backend.casework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainwatch.backend.casework.repository.EventActivityRepository;
import com.chainwatch.backend.casework.repository.PolicySuggestionRepository;
import com.chainwatch.backend.casework.service.ExcludedAddressRegistry;
import com.chainwatch.backend.detection.service.DetectionService;
import com.chainwatch.backend.event.domain.DetectionEvent;
import com.chainwatch.backend.event.domain.EventStatus;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import com.chainwatch.backend.event.repository.DetectionEventRepository;
import com.chainwatch.backend.security.JwtTokenProvider;
import com.chainwatch.backend.transaction.domain.Transaction;
import com.chainwatch.backend.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 분석가 업무 API: 작업 큐, 처리 이력·코멘트, 일괄 처리, 예외 주소 제안과 탐지 반영, 거래 상대 조회. */
@SpringBootTest(properties = {
        "chainwatch.security.jwt-enabled=true",
        "chainwatch.security.jwt-secret=test-secret-key-for-casework-api-tests-0123456789abcdef",
        // 이 테스트는 브로커 없이 탐지 서비스를 직접 호출한다.
        "chainwatch.infra.kafka-enabled=false"
})
@AutoConfigureMockMvc
class CaseworkApiTest {

    private static final String WALLET = "0x1111111111111111111111111111111111111111";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private DetectionEventRepository detectionEventRepository;
    @Autowired
    private EventActivityRepository activityRepository;
    @Autowired
    private PolicySuggestionRepository suggestionRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private ExcludedAddressRegistry registry;
    @Autowired
    private DetectionService detectionService;

    @AfterEach
    void cleanUp() {
        activityRepository.deleteAll();
        suggestionRepository.deleteAll();
        detectionEventRepository.deleteAll();
        transactionRepository.deleteAll();
        registry.reload();
    }

    private String analyst() {
        return "Bearer " + jwtTokenProvider.createToken("analyst", List.of("ROLE_ANALYST"));
    }

    private String admin() {
        return "Bearer " + jwtTokenProvider.createToken("admin", List.of("ROLE_ADMIN"));
    }

    private String viewer() {
        return "Bearer " + jwtTokenProvider.createToken("demo-viewer", List.of("ROLE_VIEWER"));
    }

    private DetectionEvent highRiskEvent(String wallet) {
        return detectionEventRepository.save(new DetectionEvent(
                EventType.FAN_OUT, RiskLevel.HIGH, 78, "Fan-out pattern detected", wallet, Instant.now(), null));
    }

    @Test
    void workQueueShowsUnassignedHighRiskThenMyCasesAfterClaiming() throws Exception {
        DetectionEvent event = highRiskEvent(WALLET);

        mockMvc.perform(get("/api/workqueue").header("Authorization", analyst()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("analyst"))
                .andExpect(jsonPath("$.myOpenCount").value(0))
                .andExpect(jsonPath("$.unassignedHighRiskCount").value(1))
                .andExpect(jsonPath("$.unassignedHighRisk[0].id").value(event.getId()));

        mockMvc.perform(post("/api/events/bulk-status").header("Authorization", analyst())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + event.getId() + "],\"status\":\"ACKNOWLEDGED\",\"assignee\":\"analyst\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1));

        mockMvc.perform(get("/api/workqueue").header("Authorization", analyst()))
                .andExpect(jsonPath("$.myOpenCount").value(1))
                .andExpect(jsonPath("$.unassignedHighRiskCount").value(0))
                .andExpect(jsonPath("$.myCases[0].id").value(event.getId()));
    }

    @Test
    void statusChangeAndCommentsAreRecordedAsEventHistory() throws Exception {
        DetectionEvent event = highRiskEvent(WALLET);

        mockMvc.perform(patch("/api/events/" + event.getId() + "/status").header("Authorization", analyst())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INVESTIGATING\",\"assignee\":\"analyst\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/events/" + event.getId() + "/comments").header("Authorization", analyst())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"수신 주소 33개 확인\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("COMMENT"));

        // 조회자도 이력은 볼 수 있다(최신순). 코멘트 작성은 할 수 없다.
        mockMvc.perform(get("/api/events/" + event.getId() + "/activities").header("Authorization", viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].detail").value("수신 주소 33개 확인"))
                .andExpect(jsonPath("$[0].actor").value("analyst"));
        mockMvc.perform(post("/api/events/" + event.getId() + "/comments").header("Authorization", viewer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void bulkCloseRequiresReasonAndAppliesToEverySelectedEvent() throws Exception {
        DetectionEvent first = highRiskEvent(WALLET);
        DetectionEvent second = highRiskEvent(WALLET);
        String ids = "[" + first.getId() + "," + second.getId() + ",999999]";

        mockMvc.perform(post("/api/events/bulk-status").header("Authorization", analyst())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":" + ids + ",\"status\":\"FALSE_POSITIVE\"}"))
                .andExpect(status().isBadRequest());
        assertThat(detectionEventRepository.findById(first.getId()).orElseThrow().getStatus())
                .isEqualTo(EventStatus.NEW);

        mockMvc.perform(post("/api/events/bulk-status").header("Authorization", analyst())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":" + ids
                                + ",\"status\":\"FALSE_POSITIVE\",\"falsePositiveReason\":\"거래소 출금 배치\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(2))
                .andExpect(jsonPath("$.missingIds[0]").value(999999));
        assertThat(detectionEventRepository.findById(second.getId()).orElseThrow().getStatus())
                .isEqualTo(EventStatus.FALSE_POSITIVE);
    }

    @Test
    void acceptedExclusionSuggestionStopsDetectionForThatWalletUntilRevoked() throws Exception {
        DetectionEvent event = highRiskEvent(WALLET);
        String body = "{\"address\":\"" + WALLET + "\",\"reason\":\"거래소 핫월렛\",\"eventId\":" + event.getId() + "}";

        String response = mockMvc.perform(post("/api/policy-suggestions").header("Authorization", analyst())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(response.replaceAll(".*\"id\":(\\d+).*", "$1"));

        // 같은 주소의 중복 제안은 거부, 분석가는 결정할 수 없다.
        mockMvc.perform(post("/api/policy-suggestions").header("Authorization", analyst())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/admin/policy-suggestions/" + id).header("Authorization", analyst())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ACCEPT\"}"))
                .andExpect(status().isForbidden());
        assertThat(registry.isExcluded(WALLET)).isFalse();

        mockMvc.perform(patch("/api/admin/policy-suggestions/" + id).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ACCEPT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.decidedBy").value("admin"));
        assertThat(registry.isExcluded(WALLET.toUpperCase().replace("0X", "0x"))).isTrue();

        // 대규모 이체는 받는 지갑이 탐지 대상이다. 그 지갑이 예외면 이벤트를 만들지 않는다.
        long before = detectionEventRepository.count();
        detectionService.analyzeTransaction(largeTransfer("0xexcluded-tx-1"));
        assertThat(detectionEventRepository.count()).isEqualTo(before);

        mockMvc.perform(patch("/api/admin/policy-suggestions/" + id).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"REVOKE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));
        detectionService.analyzeTransaction(largeTransfer("0xexcluded-tx-2"));
        assertThat(detectionEventRepository.count()).isEqualTo(before + 1);
    }

    @Test
    void counterpartiesSummarizeWhereTheWalletSentAndReceived() throws Exception {
        String other = "0x2222222222222222222222222222222222222222";
        transactionRepository.save(tx("0xcp-1", WALLET, other, "1.5"));
        transactionRepository.save(tx("0xcp-2", WALLET, other, "2.5"));
        transactionRepository.save(tx("0xcp-3", other, WALLET, "0.5"));

        mockMvc.perform(get("/api/wallets/" + WALLET + "/counterparties").header("Authorization", viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outgoingTxCount").value(2))
                .andExpect(jsonPath("$.incomingTxCount").value(1))
                .andExpect(jsonPath("$.recipients[0].address").value(other))
                .andExpect(jsonPath("$.recipients[0].txCount").value(2))
                .andExpect(jsonPath("$.recipients[0].totalAmount").value(4.0))
                .andExpect(jsonPath("$.senders[0].txCount").value(1));
    }

    private Transaction largeTransfer(String hash) {
        return transactionRepository.save(tx(hash, "0x3333333333333333333333333333333333333333", WALLET, "500"));
    }

    private static Transaction tx(String hash, String from, String to, String amount) {
        return new Transaction(hash, from, to, new BigDecimal(amount), new BigDecimal("0.000021"),
                1L, Instant.now(), null);
    }
}
