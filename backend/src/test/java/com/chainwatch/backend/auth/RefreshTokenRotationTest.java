package com.chainwatch.backend.auth;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 리프레시 토큰 회전 규칙 회귀 테스트.
 * - refresh는 새 액세스+리프레시 쌍을 발급하고 이전 리프레시 토큰을 폐기한다.
 * - 회전이 끝난 토큰을 다시 제시하면(탈취 신호) 해당 사용자의 모든 세션이 폐기된다.
 */
@SpringBootTest(properties = {
        "chainwatch.security.jwt-enabled=true",
        "chainwatch.security.jwt-secret=test-secret-key-for-refresh-rotation-tests-0123456789"
})
@AutoConfigureMockMvc
class RefreshTokenRotationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void refreshRotatesTokenAndNewPairWorks() throws Exception {
        JsonNode login = login();
        String firstRefresh = login.get("refreshToken").asText();

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + firstRefresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.user.username").value("admin"))
                .andReturn();
        JsonNode refreshed = objectMapper.readTree(refreshResult.getResponse().getContentAsString());

        mockMvc.perform(get("/api/events")
                        .header("Authorization", "Bearer " + refreshed.get("accessToken").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void reusingRotatedTokenRevokesAllSessions() throws Exception {
        JsonNode login = login();
        String firstRefresh = login.get("refreshToken").asText();

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + firstRefresh + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String secondRefresh = objectMapper.readTree(refreshResult.getResponse().getContentAsString())
                .get("refreshToken").asText();

        // 이미 회전된 토큰 재사용 → 401 + 전 세션 폐기
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + firstRefresh + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // 재사용 감지 이후에는 정상 발급된 후속 토큰도 폐기되어야 한다
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + secondRefresh + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void unknownRefreshTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"definitely-not-a-real-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    private JsonNode login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"chainwatch\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
