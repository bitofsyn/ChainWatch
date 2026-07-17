package com.chainwatch.backend.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainwatch.backend.security.JwtTokenProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** /api/users/** 전체가 ADMIN 전용임을 검증한다: 익명 401, ANALYST 403, ADMIN 허용. */
@SpringBootTest(properties = {
        "chainwatch.security.jwt-enabled=true",
        "chainwatch.security.jwt-secret=test-secret-key-for-user-api-authorization-tests-0123456789"
})
@AutoConfigureMockMvc
class UserApiAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String adminToken() {
        return jwtTokenProvider.createToken("admin", List.of("ROLE_ADMIN"));
    }

    private String analystToken() {
        return jwtTokenProvider.createToken("analyst", List.of("ROLE_ANALYST"));
    }

    @Test
    void anonymousUserListReturns401() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void analystUserListReturns403() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + analystToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void analystUserCreateReturns403() throws Exception {
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + analystToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"blocked\",\"role\":\"ANALYST\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListUsers() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("admin"));
    }

    @Test
    void adminCanCreateUserAndReceivesGeneratedPasswordOnce() throws Exception {
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"authz-created\",\"role\":\"ANALYST\",\"displayName\":\"권한 테스트\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.username").value("authz-created"))
                .andExpect(jsonPath("$.user.role").value("ANALYST"))
                .andExpect(jsonPath("$.initialPassword").isNotEmpty());
    }
}
