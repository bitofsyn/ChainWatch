package com.chainwatch.backend.staking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** A root-pinned observation, not a reward calculation or independent consensus verification. */
@Component
public class BeaconObservationClient {
    private final StakingProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;
    public BeaconObservationClient(StakingProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(properties.requestTimeout()).build();
    }
    public record Validator(String index, String publicKey, String balanceGwei, String effectiveBalanceGwei,
            String status, boolean slashed, String activationEpoch, String exitEpoch, String withdrawableEpoch) {}
    public record Snapshot(String stateRoot, String blockRoot, String slot, String epoch, Instant stateTime,
            String clientVersion, Instant checkpointObservedAt,
            List<Validator> validators, String rawEvidence) {}
    public static class ObservationFailure extends RuntimeException {
        public ObservationFailure(String code) { super(code); }
    }
    public Snapshot observe() {
        JsonNode genesis = get("/eth/v1/beacon/genesis");
        if (!properties.genesisValidatorsRoot().equalsIgnoreCase(text(genesis.path("data"), "genesis_validators_root")))
            throw new ObservationFailure("NETWORK_MISMATCH");
        JsonNode version = get("/eth/v1/node/version");
        String clientVersion = text(version.path("data"), "version");
        if (clientVersion.length() > 250) throw new ObservationFailure("INVALID_RESPONSE");
        JsonNode spec = get("/eth/v1/config/spec");
        BigInteger secondsPerSlot = new BigInteger(uint(spec.path("data"), "SECONDS_PER_SLOT"));
        BigInteger slotsPerEpoch = new BigInteger(uint(spec.path("data"), "SLOTS_PER_EPOCH"));
        if (secondsPerSlot.signum() == 0 || slotsPerEpoch.signum() == 0) throw new ObservationFailure("INVALID_CHAIN_SPEC");
        JsonNode root = get("/eth/v1/beacon/headers/finalized");
        verified(root);
        JsonNode header = root.path("data").path("header").path("message");
        if (!bool(root.path("data"), "canonical")) throw new ObservationFailure("NON_CANONICAL_CHECKPOINT");
        String blockRoot = text(root.path("data"), "root");
        String stateRoot = text(header, "state_root");
        String slot = uint(header, "slot");
        String epoch = new BigInteger(slot).divide(slotsPerEpoch).toString();
        Instant stateTime;
        try {
            stateTime = Instant.ofEpochSecond(new BigInteger(uint(genesis.path("data"), "genesis_time"))
                    .add(new BigInteger(slot).multiply(secondsPerSlot)).longValueExact());
        } catch (ArithmeticException | java.time.DateTimeException e) { throw new ObservationFailure("INVALID_CHECKPOINT_TIME"); }
        if (stateTime.isAfter(Instant.now().plusSeconds(30))) throw new ObservationFailure("FUTURE_CHECKPOINT");
        if (stateTime.plus(properties.maxCheckpointAge()).isBefore(Instant.now())) throw new ObservationFailure("STALE_CHECKPOINT");
        if (!blockRoot.matches("0x[0-9a-fA-F]{64}")) throw new ObservationFailure("INVALID_BLOCK_ROOT");
        if (!stateRoot.matches("0x[0-9a-fA-F]{64}")) throw new ObservationFailure("INVALID_STATE_ROOT");
        Instant checkpointObservedAt = Instant.now();
        List<Validator> validators = new ArrayList<>();
        var evidence = mapper.createObjectNode();
        evidence.set("spec", spec); evidence.set("genesis", genesis); evidence.set("version", version); evidence.set("checkpoint", root);
        var responses = evidence.putArray("validators");
        for (String requested : properties.validatorIds()) {
            JsonNode response = get("/eth/v1/beacon/states/" + stateRoot + "/validators/" + requested);
            verified(response);
            JsonNode data = response.path("data"), validator = data.path("validator");
            String index = uint(data, "index"), pubkey = text(validator, "pubkey");
            if (!pubkey.matches("0x[0-9a-fA-F]{96}") || !(requested.equals(index) || requested.equalsIgnoreCase(pubkey)))
                throw new ObservationFailure("VALIDATOR_MISMATCH");
            String status = text(data, "status");
            if (!List.of("pending_initialized", "pending_queued", "active_ongoing", "active_exiting", "active_slashed",
                    "exited_unslashed", "exited_slashed", "withdrawal_possible", "withdrawal_done").contains(status))
                throw new ObservationFailure("UNKNOWN_VALIDATOR_STATUS");
            validators.add(new Validator(index, pubkey, uint(data, "balance"), uint(validator, "effective_balance"),
                    status, bool(validator, "slashed"), uint(validator, "activation_epoch"),
                    uint(validator, "exit_epoch"), uint(validator, "withdrawable_epoch")));
            responses.add(response);
        }
        if (validators.stream().map(Validator::index).distinct().count() != validators.size())
            throw new ObservationFailure("DUPLICATE_VALIDATOR");
        return new Snapshot(stateRoot, blockRoot, slot, epoch, stateTime, clientVersion, checkpointObservedAt, List.copyOf(validators), evidence.toString());
    }
    private JsonNode get(String path) {
        try {
            String base = properties.beaconUrl().toString().replaceAll("/+$", "");
            var request = HttpRequest.newBuilder(java.net.URI.create(base + path))
                    .timeout(properties.requestTimeout()).header("Accept", "application/json").GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new ObservationFailure("BEACON_HTTP_" + response.statusCode());
            JsonNode node = mapper.readTree(response.body());
            if (node == null || !node.isObject()) throw new ObservationFailure("INVALID_RESPONSE");
            return node;
        } catch (ObservationFailure e) { throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new ObservationFailure("BEACON_INTERRUPTED");
        } catch (java.net.http.HttpTimeoutException e) { throw new ObservationFailure("BEACON_TIMEOUT");
        } catch (java.io.IOException e) { throw new ObservationFailure("BEACON_IO_ERROR"); }
    }
    private static void verified(JsonNode node) {
        if (bool(node, "execution_optimistic")) throw new ObservationFailure("EXECUTION_OPTIMISTIC");
        if (!bool(node, "finalized")) throw new ObservationFailure("NOT_FINALIZED");
    }
    private static boolean bool(JsonNode node, String name) {
        if (!node.path(name).isBoolean()) throw new ObservationFailure("INVALID_RESPONSE");
        return node.path(name).booleanValue();
    }
    private static String text(JsonNode node, String name) {
        if (!node.path(name).isTextual() || node.path(name).asText().isBlank()) throw new ObservationFailure("INVALID_RESPONSE");
        return node.path(name).asText();
    }
    private static String uint(JsonNode node, String name) {
        String value = text(node, name);
        if (!value.matches("0|[1-9][0-9]{0,19}") || new BigInteger(value).bitLength() > 64)
            throw new ObservationFailure("INVALID_UINT64");
        return value;
    }
}
