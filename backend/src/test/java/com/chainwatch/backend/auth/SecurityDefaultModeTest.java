package com.chainwatch.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Backward compatibility: with jwt-enabled=false (the default),
 * every /api endpoint remains accessible without authentication.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityDefaultModeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiEndpointsRemainPublicWhenJwtDisabled() throws Exception {
        mockMvc.perform(get("/api/events")).andExpect(status().isOk());
    }

    @Test
    void healthEndpointRemainsPublic() throws Exception {
        // Health may report 503(DOWN) when local infra (Redis/Kafka) is offline.
        // This test only guarantees the endpoint is reachable without authentication.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
    }
}
