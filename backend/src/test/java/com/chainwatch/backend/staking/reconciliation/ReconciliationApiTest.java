package com.chainwatch.backend.staking.reconciliation;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.chainwatch.backend.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:reconciliation-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "chainwatch.security.jwt-enabled=true", "chainwatch.security.jwt-secret=reconciliation-secret-test-012345678901234567890123456789",
    "chainwatch.staking.enabled=true", "chainwatch.staking.network=local-test", "chainwatch.staking.beacon-url=http://localhost:59999",
    "chainwatch.staking.genesis-validators-root=0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
    "chainwatch.staking.validator-ids=0", "chainwatch.staking.reconciliation.enabled=true",
    "chainwatch.staking.reconciliation.execution-url=http://localhost:59998"
})
@AutoConfigureMockMvc
class ReconciliationApiTest {
    @Autowired MockMvc mvc; @Autowired JwtTokenProvider tokens; @Autowired ReconciliationRepository repository;
    @MockitoBean EpochReconciliationClient client;
    ObjectMapper m=new ObjectMapper();
    @BeforeEach void clean(){repository.deleteAll();}
    String token(String role){return "Bearer "+tokens.createToken(role,List.of("ROLE_"+role));}
    @Test void analystCanReadButCannotTrigger() throws Exception {
        mvc.perform(post("/api/ops/staking/reconciliations").header("Authorization",token("ANALYST")).contentType("application/json").content("{\"epoch\":8}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/ops/staking/reconciliations").header("Authorization",token("ANALYST"))).andExpect(status().isOk());verifyNoInteractions(client);
    }
    @Test void failureEvidenceAndRepeatedAttemptsRemainDistinct() throws Exception {
        when(client.reconcile(8)).thenReturn(new EpochReconciliationClient.Result("MATCHED",null,m.createObjectNode().put("settlementEligible",false),m.createArrayNode()))
            .thenReturn(new EpochReconciliationClient.Result("INCOMPLETE","UPSTREAM_TIMEOUT",m.createObjectNode(),m.createArrayNode().add("partial proof")));
        for(String status:List.of("MATCHED","INCOMPLETE")) mvc.perform(post("/api/ops/staking/reconciliations").header("Authorization",token("ADMIN")).contentType("application/json").content("{\"epoch\":8}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(status));
        assertThat(repository.count()).isEqualTo(2);
        var failed=repository.findAll().stream().filter(r->r.getStatus().equals("INCOMPLETE")).findFirst().orElseThrow();
        mvc.perform(get("/api/ops/staking/reconciliations/"+failed.getId()+"/evidence").header("Authorization",token("ANALYST")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0]").value("partial proof"));
    }
    @Test void invalidEpochDoesNotCreateAttempt() throws Exception {
        mvc.perform(post("/api/ops/staking/reconciliations").header("Authorization",token("ADMIN")).contentType("application/json").content("{\"epoch\":0}"))
            .andExpect(status().isBadRequest());assertThat(repository.count()).isZero();verifyNoInteractions(client);
    }
    @Test void fractionalEpochIsNotSilentlyTruncated() throws Exception {
        mvc.perform(post("/api/ops/staking/reconciliations").header("Authorization",token("ADMIN")).contentType("application/json").content("{\"epoch\":1.5}"))
            .andExpect(status().isBadRequest());assertThat(repository.count()).isZero();verifyNoInteractions(client);
    }
    @Test void missingEvidenceReturns404() throws Exception {
        mvc.perform(get("/api/ops/staking/reconciliations/999999/evidence").header("Authorization",token("ANALYST")))
            .andExpect(status().isNotFound());
    }
}
