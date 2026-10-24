package com.chainwatch.backend.staking;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;

class BeaconObservationClientTest {
    static final String ROOT = "0x" + "a".repeat(64), GENESIS = "0x" + "b".repeat(64), KEY = "0x" + "c".repeat(96);
    MockWebServer server;
    @BeforeEach void start() throws Exception { server = new MockWebServer(); server.start(); }
    @AfterEach void stop() throws Exception { server.shutdown(); }
    BeaconObservationClient client() { return new BeaconObservationClient(new StakingProperties(true, "local-test", server.url("/").uri(), GENESIS,
            List.of("42"), Duration.ofMillis(300), Duration.ofMinutes(15), Duration.ofMinutes(30)), new ObjectMapper()); }
    void response(String body) { server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(body)); }
    void prefix() {
        response("{\"data\":{\"genesis_time\":\"" + (java.time.Instant.now().getEpochSecond() - 3840 - 600) + "\",\"genesis_validators_root\":\"" + GENESIS + "\"}}");
        response("{\"data\":{\"version\":\"fixture-client/1\"}}");
        response("{\"data\":{\"SECONDS_PER_SLOT\":\"12\",\"SLOTS_PER_EPOCH\":\"32\"}}");
    }
    void root() { response(checkpoint("320", true)); }
    static String checkpoint(String slot, boolean canonical) {
        return """
            {"execution_optimistic":false,"finalized":true,"data":{"root":"%s","canonical":%s,
            "header":{"message":{"state_root":"%s","slot":"%s"}}}}
            """.formatted(ROOT, canonical, ROOT, slot);
    }
    static String validator() {
        return """
            {"execution_optimistic":false,"finalized":true,"data":{"index":"42","balance":"32000000001",
            "status":"active_ongoing","validator":{"pubkey":"%s","effective_balance":"32000000000",
            "slashed":false,"activation_epoch":"1","exit_epoch":"18446744073709551615","withdrawable_epoch":"18446744073709551615"}}}
            """.formatted(KEY);
    }
    @Test void pinsValidatorRequestToRootAndPreservesIntegerUnits() throws Exception {
        prefix(); root(); response(validator());
        var snapshot = client().observe();
        assertThat(snapshot.stateRoot()).isEqualTo(ROOT);
        assertThat(snapshot.epoch()).isEqualTo("10");
        assertThat(snapshot.slot()).isEqualTo("320");
        assertThat(snapshot.validators().getFirst().balanceGwei()).isEqualTo("32000000001");
        assertThat(snapshot.validators().getFirst().exitEpoch()).isEqualTo("18446744073709551615");
        assertThat(snapshot.rawEvidence()).contains("fixture-client/1", GENESIS);
        server.takeRequest(); server.takeRequest(); server.takeRequest(); server.takeRequest();
        assertThat(server.takeRequest().getPath()).isEqualTo("/eth/v1/beacon/states/" + ROOT + "/validators/42");
    }
    @Test void rejectsWrongNetworkBeforeReadingBalances() {
        response("{\"data\":{\"genesis_validators_root\":\"" + ROOT + "\"}}");
        assertThatThrownBy(() -> client().observe()).hasMessage("NETWORK_MISMATCH");
        assertThat(server.getRequestCount()).isEqualTo(1);
    }
    @Test void refusesOptimisticCheckpoint() {
        prefix(); response("{\"execution_optimistic\":true,\"finalized\":true}");
        assertThatThrownBy(() -> client().observe()).hasMessage("EXECUTION_OPTIMISTIC");
    }
    @Test void missingFinalityIsNotSilentlyAccepted() {
        prefix(); response("{\"execution_optimistic\":false,\"data\":{}}");
        assertThatThrownBy(() -> client().observe()).hasMessage("INVALID_RESPONSE");
    }
    @Test void rejectsUnfinalizedValidatorResponse() {
        prefix(); root(); response(validator().replace("\"finalized\":true", "\"finalized\":false"));
        assertThatThrownBy(() -> client().observe()).hasMessage("NOT_FINALIZED");
    }
    @Test void doesNotInterpretMissingValidatorAsZeroBalance() {
        prefix(); root(); server.enqueue(new MockResponse().setResponseCode(404).setBody("not found"));
        assertThatThrownBy(() -> client().observe()).hasMessage("BEACON_HTTP_404");
    }
    @Test void rejectsMismatchedValidator() {
        prefix(); root(); response(validator().replace("\"42\"", "\"43\""));
        assertThatThrownBy(() -> client().observe()).hasMessage("VALIDATOR_MISMATCH");
    }
    @Test void rejectsOldCheckpointDespiteFreshHttpResponse() {
        prefix(); response(checkpoint("0", true));
        assertThatThrownBy(() -> client().observe()).hasMessage("STALE_CHECKPOINT");
    }
    @Test void rejectsFutureCheckpoint() {
        prefix(); response(checkpoint("999999", true));
        assertThatThrownBy(() -> client().observe()).hasMessage("FUTURE_CHECKPOINT");
    }
    @Test void rejectsNonCanonicalCheckpoint() {
        prefix(); response(checkpoint("320", false));
        assertThatThrownBy(() -> client().observe()).hasMessage("NON_CANONICAL_CHECKPOINT");
    }
    @Test void rejectsNegativeBalance() {
        prefix(); root(); response(validator().replace("32000000001", "-1"));
        assertThatThrownBy(() -> client().observe()).hasMessage("INVALID_UINT64");
    }
    @Test void timesOutWithoutPublishingPartialSnapshot() {
        server.enqueue(new MockResponse().setHeadersDelay(1, java.util.concurrent.TimeUnit.SECONDS).setBody("{}"));
        assertThatThrownBy(() -> client().observe()).hasMessage("BEACON_TIMEOUT");
    }
}
