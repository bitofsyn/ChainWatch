package com.chainwatch.backend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainwatch.backend.event.domain.DetectionEvent;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import com.chainwatch.backend.event.repository.DetectionEventRepository;
import com.chainwatch.backend.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * ADMIN/ANALYST 권한 분리 회귀 테스트.
 * - 관리자 제어 API(수집 트리거)는 ADMIN 전용: ANALYST는 403, 익명은 401.
 * - 조회/분석가 workflow API는 ANALYST 이상이면 접근 가능.
 * - 감사 로그 열람은 ADMIN 전용.
 */
@SpringBootTest(properties = {
        "chainwatch.security.jwt-enabled=true",
        "chainwatch.security.jwt-secret=test-secret-key-for-role-authorization-tests-0123456789"
})
@AutoConfigureMockMvc
class SecurityRoleAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private DetectionEventRepository detectionEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanUp() {
        detectionEventRepository.deleteAll();
    }

    private String adminToken() {
        return jwtTokenProvider.createToken("admin", List.of("ROLE_ADMIN"));
    }

    private String analystToken() {
        return jwtTokenProvider.createToken("analyst", List.of("ROLE_ANALYST"));
    }

    @Test
    void anonymousCollectorControlReturns401() throws Exception {
        mockMvc.perform(post("/api/collector/blocks/latest"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void analystCollectorControlReturns403() throws Exception {
        mockMvc.perform(post("/api/collector/blocks/latest")
                        .header("Authorization", "Bearer " + analystToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void analystCollectBlockReturns403() throws Exception {
        mockMvc.perform(post("/api/collector/blocks/123")
                        .header("Authorization", "Bearer " + analystToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousReadEndpointReturns401() throws Exception {
        mockMvc.perform(get("/api/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void analystCanReadEvents() throws Exception {
        mockMvc.perform(get("/api/events")
                        .header("Authorization", "Bearer " + analystToken()))
                .andExpect(status().isOk());
    }

    @Test
    void analystCanReadCollectorState() throws Exception {
        mockMvc.perform(get("/api/collector/state")
                        .header("Authorization", "Bearer " + analystToken()))
                .andExpect(status().isOk());
    }

    @Test
    void analystCanUpdateEventStatus() throws Exception {
        DetectionEvent event = detectionEventRepository.save(new DetectionEvent(
                EventType.LARGE_TRANSFER, RiskLevel.HIGH, 90,
                "Large transfer detected", "0xabc", Instant.now(), null));

        mockMvc.perform(patch("/api/events/" + event.getId() + "/status")
                        .header("Authorization", "Bearer " + analystToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACKNOWLEDGED\",\"assignee\":\"analyst\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.assignee").value("analyst"));
    }

    @Test
    void analystAuditLogAccessReturns403() throws Exception {
        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + analystToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminCanReadAuditLogs() throws Exception {
        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    void analystLoginIssuesUsableAnalystToken() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"analyst\",\"password\":\"chainwatch\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper
                .readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(get("/api/events").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/collector/blocks/latest").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
