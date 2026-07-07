package com.chainwatch.backend.event.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainwatch.backend.audit.domain.AuditLog;
import com.chainwatch.backend.audit.repository.AuditLogRepository;
import com.chainwatch.backend.event.domain.DetectionEvent;
import com.chainwatch.backend.event.domain.EventStatus;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import com.chainwatch.backend.event.repository.DetectionEventRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 분석가 workflow 검증 규칙 + 감사 로그 기록 API 테스트 (기본 jwt-disabled 모드). */
@SpringBootTest
@AutoConfigureMockMvc
class EventWorkflowApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DetectionEventRepository detectionEventRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    /**
     * H2 in-memory DB(DB_CLOSE_DELAY=-1)는 같은 JVM의 다른 테스트 컨텍스트와 공유되므로
     * 다른 테스트가 남긴 감사 로그가 개수 검증을 오염시키지 않도록 시작 시에도 비운다.
     */
    @BeforeEach
    void setUp() {
        detectionEventRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    @AfterEach
    void cleanUp() {
        detectionEventRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    @Test
    void resolveWithoutReasonReturns400() throws Exception {
        DetectionEvent event = seedEvent();

        mockMvc.perform(patch("/api/events/" + event.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void falsePositiveWithoutReasonReturns400() throws Exception {
        DetectionEvent event = seedEvent();

        mockMvc.perform(patch("/api/events/" + event.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FALSE_POSITIVE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void resolveWithReasonReturnsWorkflowFields() throws Exception {
        DetectionEvent event = seedEvent();

        mockMvc.perform(patch("/api/events/" + event.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"RESOLVED","assignee":"alice",
                                 "resolutionReason":"Confirmed legitimate exchange transfer",
                                 "notes":"cross-checked with hot wallet list"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.assignee").value("alice"))
                .andExpect(jsonPath("$.resolutionReason").value("Confirmed legitimate exchange transfer"))
                .andExpect(jsonPath("$.notes").value("cross-checked with hot wallet list"))
                .andExpect(jsonPath("$.statusChangedAt", notNullValue()));

        DetectionEvent updated = detectionEventRepository.findById(event.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(EventStatus.RESOLVED);
        assertThat(updated.getStatusChangedAt()).isNotNull();
    }

    @Test
    void falsePositiveWithReasonIsAcceptedAndExposedInDetail() throws Exception {
        DetectionEvent event = seedEvent();

        mockMvc.perform(patch("/api/events/" + event.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FALSE_POSITIVE\",\"falsePositiveReason\":\"Internal rebalancing wallet\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FALSE_POSITIVE"));

        mockMvc.perform(get("/api/events/" + event.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.falsePositiveReason").value("Internal rebalancing wallet"))
                .andExpect(jsonPath("$.statusChangedAt", notNullValue()));
    }

    @Test
    void statusChangeIsAudited() throws Exception {
        DetectionEvent event = seedEvent();

        mockMvc.perform(patch("/api/events/" + event.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACKNOWLEDGED\"}"))
                .andExpect(status().isOk());

        List<AuditLog> entries = auditLogRepository.findAll();
        assertThat(entries).hasSize(1);
        AuditLog entry = entries.get(0);
        assertThat(entry.getAction()).isEqualTo("EVENT_STATUS_CHANGE");
        assertThat(entry.getTargetType()).isEqualTo("DETECTION_EVENT");
        assertThat(entry.getTargetId()).isEqualTo(String.valueOf(event.getId()));
        assertThat(entry.getDetail()).contains("NEW -> ACKNOWLEDGED");
        assertThat(entry.getActor()).isEqualTo("anonymous");
        assertThat(entry.getCreatedAt()).isNotNull();
    }

    @Test
    void rejectedStatusChangeIsNotAudited() throws Exception {
        DetectionEvent event = seedEvent();

        mockMvc.perform(patch("/api/events/" + event.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isBadRequest());

        assertThat(auditLogRepository.count()).isZero();
    }

    @Test
    void auditLogEndpointReturnsRecordedEntries() throws Exception {
        DetectionEvent event = seedEvent();
        mockMvc.perform(patch("/api/events/" + event.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INVESTIGATING\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/audit-logs").param("action", "EVENT_STATUS_CHANGE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("EVENT_STATUS_CHANGE"))
                .andExpect(jsonPath("$.content[0].actor").value("anonymous"))
                .andExpect(jsonPath("$.content[0].targetId").value(String.valueOf(event.getId())));
    }

    @Test
    void eventListSupportsStatusFilterIncludingLegacyNullAsNew() throws Exception {
        DetectionEvent legacyNew = seedEvent();
        DetectionEvent resolved = seedEvent();
        mockMvc.perform(patch("/api/events/" + resolved.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"resolutionReason\":\"done\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/events").param("status", "NEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(legacyNew.getId()));

        mockMvc.perform(get("/api/events").param("status", "RESOLVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(resolved.getId()));
    }

    private DetectionEvent seedEvent() {
        return detectionEventRepository.save(new DetectionEvent(
                EventType.LARGE_TRANSFER,
                RiskLevel.HIGH,
                90,
                "Large transfer detected",
                "0xabc",
                Instant.now(),
                null
        ));
    }
}
