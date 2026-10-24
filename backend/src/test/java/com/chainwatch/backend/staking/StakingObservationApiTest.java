package com.chainwatch.backend.staking;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.chainwatch.backend.security.JwtTokenProvider;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:staking-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "chainwatch.security.jwt-enabled=true",
    "chainwatch.security.jwt-secret=staking-test-secret-for-authorization-01234567890123456789",
    "chainwatch.staking.enabled=true", "chainwatch.staking.network=fixture-network",
    "chainwatch.staking.beacon-url=http://localhost:59999",
    "chainwatch.staking.genesis-validators-root=0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
    "chainwatch.staking.validator-ids=42"
})
@AutoConfigureMockMvc
class StakingObservationApiTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired StakingObservationRepository repository;
    @MockitoBean BeaconObservationClient client;
    @BeforeEach void clean() { repository.deleteAll(); }
    String token(String role) { return "Bearer " + tokens.createToken(role, List.of("ROLE_" + role)); }
    @Test void readOnlyAnalystCannotTriggerNetworkObservation() throws Exception {
        mvc.perform(post("/api/ops/staking/observations").header("Authorization", token("ANALYST"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/ops/staking/observations").header("Authorization", token("ANALYST"))).andExpect(status().isOk());
        mvc.perform(get("/api/ops/staking/observations")).andExpect(status().isUnauthorized());
        verifyNoInteractions(client);
    }
    @Test void persistsFailureSeparatelyFromLastSuccessfulObservation() throws Exception {
        var snapshot = new BeaconObservationClient.Snapshot(BeaconObservationClientTest.ROOT, BeaconObservationClientTest.ROOT, "320", "10", Instant.now(), "fixture/1", Instant.now(), List.of(), "{\"proof\":true}");
        when(client.observe()).thenReturn(snapshot).thenThrow(new BeaconObservationClient.ObservationFailure("BEACON_TIMEOUT"));
        mvc.perform(post("/api/ops/staking/observations").header("Authorization", token("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OBSERVED"));
        mvc.perform(post("/api/ops/staking/observations").header("Authorization", token("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INCOMPLETE"))
                .andExpect(jsonPath("$.errorCode").value("BEACON_TIMEOUT")).andExpect(jsonPath("$.stateRoot").isEmpty());
        assertThat(repository.count()).isEqualTo(2);
        mvc.perform(get("/api/ops/staking/observations").header("Authorization", token("ANALYST")))
                .andExpect(jsonPath("$.observations[0].status").value("INCOMPLETE"))
                .andExpect(jsonPath("$.observations[1].status").value("OBSERVED"));
    }
    @Test void evidenceAndStalenessAreVisibleWithoutCallingBeaconAgain() throws Exception {
        when(client.observe()).thenReturn(new BeaconObservationClient.Snapshot(BeaconObservationClientTest.ROOT, BeaconObservationClientTest.ROOT, "320", "10", Instant.now(),
                "fixture/1", Instant.now().minusSeconds(1000), List.of(), "{\"proof\":true}"));
        mvc.perform(post("/api/ops/staking/observations").header("Authorization", token("ADMIN")))
                .andExpect(jsonPath("$.stale").value(true));
        Long id = repository.findAll().getFirst().getId();
        mvc.perform(get("/api/ops/staking/observations/" + id + "/evidence").header("Authorization", token("ANALYST")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.proof").value(true));
        verify(client, times(1)).observe();
    }
}
