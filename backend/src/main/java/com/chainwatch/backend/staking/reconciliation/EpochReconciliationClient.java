package com.chainwatch.backend.staking.reconciliation;

import static com.chainwatch.backend.staking.reconciliation.ChainEvidence.*;
import com.chainwatch.backend.staking.StakingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigInteger;
import java.util.*;
import org.springframework.stereotype.Component;

/** Local minimal Electra/Fulu accounting; never authorizes a payment or infers rewards from residuals. */
@Component
public class EpochReconciliationClient {
    private final StakingProperties staking;
    private final ReconciliationProperties config;
    private final ObjectMapper mapper;
    public EpochReconciliationClient(StakingProperties staking, ReconciliationProperties config, ObjectMapper mapper) {
        this.staking = staking; this.config = config; this.mapper = mapper;
    }
    public record Result(String status, String errorCode, JsonNode report, JsonNode evidence) {}
    public Result reconcile(long epoch) {
        var io = new ChainEvidence(mapper, staking.requestTimeout());
        ObjectNode report = mapper.createObjectNode().put("accountingEpoch", Long.toString(epoch))
                .put("attestationEpoch", Long.toString(epoch - 1)).put("settlementEligible", false)
                .put("scope", "LOCAL_MINIMAL_ELECTRA_FULU_V1");
        try {
            require(config.enabled() && staking.enabled(), "RECONCILIATION_DISABLED");
            require(epoch > 0 && epoch <= 1_000_000, "INVALID_EPOCH");
            JsonNode genesis = get(io, "/eth/v1/beacon/genesis").path("data");
            require(staking.genesisValidatorsRoot().equalsIgnoreCase(root(genesis, "genesis_validators_root")), "NETWORK_MISMATCH");
            JsonNode spec = get(io, "/eth/v1/config/spec").path("data");
            // Full states are deliberately bounded to a small devnet; no mainnet full-state downloads.
            require("minimal".equals(text(spec, "PRESET_BASE")), "UNSUPPORTED_PRESET");
            long slots = uint(spec, "SLOTS_PER_EPOCH").longValueExact();
            require(slots > 0 && slots <= 32, "UNSUPPORTED_EPOCH_SIZE");
            BigInteger chainId = uint(spec, "DEPOSIT_CHAIN_ID");
            require(chainId.equals(hex(io.rpc(config.executionUrl(), "eth_chainId", mapper.createArrayNode()).asText())), "EXECUTION_NETWORK_MISMATCH");
            report.put("beaconClient", text(get(io, "/eth/v1/node/version").path("data"), "version"));
            report.put("executionClient", io.rpc(config.executionUrl(), "web3_clientVersion", mapper.createArrayNode()).asText());
            long startSlot = Math.multiplyExact(epoch, slots), endSlot = Math.addExact(startSlot, slots);
            JsonNode finalized = header(io, "finalized");
            require(uint(finalized.path("header").path("message"), "slot").compareTo(BigInteger.valueOf(endSlot)) >= 0, "EPOCH_NOT_FINALIZED");
            JsonNode startHeader = header(io, Long.toString(startSlot));
            JsonNode endHeader = header(io, Long.toString(endSlot));
            require(uint(startHeader.path("header").path("message"), "slot").equals(BigInteger.valueOf(startSlot))
                    && uint(endHeader.path("header").path("message"), "slot").equals(BigInteger.valueOf(endSlot)), "BOUNDARY_SLOT_MISMATCH");
            String startRoot = root(startHeader, "root"), endRoot = root(endHeader, "root");
            JsonNode start = state(io, startHeader, startSlot), end = state(io, endHeader, endSlot);
            require(uint(start,"genesis_time").equals(uint(genesis,"genesis_time"))
                    && uint(end,"genesis_time").equals(uint(genesis,"genesis_time")), "STATE_GENESIS_TIME_MISMATCH");
            require(text(start.path("fork"),"current_version").equals(text(end.path("fork"),"current_version")), "UNSUPPORTED_FORK_TRANSITION");
            report.put("genesisTime",uint(genesis,"genesis_time").toString());
            report.put("startSlot", Long.toString(startSlot)).put("endSlot", Long.toString(endSlot))
                    .put("startBlockRoot", startRoot).put("endBlockRoot", endRoot)
                    .put("interval", "post-state(startSlot) -> post-state(endSlot), blocks (startSlot,endSlot]");
            Map<String, Account> accounts = accounts(start, end);
            var ids = mapper.createArrayNode(); accounts.keySet().forEach(ids::add);
            // At the E -> E+1 transition, process_rewards_and_penalties uses previous_epoch E-1.
            // The interval also includes the E+1 first block, but excludes the E first block.
            JsonNode att = io.beacon(staking.beaconUrl(), "/eth/v1/beacon/rewards/attestations/" + (epoch - 1), ids);
            verified(att);
            Set<String> seen = new HashSet<>();
            for (JsonNode reward : array(att.path("data"), "total_rewards")) {
                String id = uint(reward, "validator_index").toString();
                require(accounts.containsKey(id) && seen.add(id), "ATTESTATION_VALIDATOR_MISMATCH");
                for (String component : List.of("head", "target", "source", "inactivity")) {
                    Account a=accounts.get(id); BigInteger value=signed(reward,component); a.att=a.att.add(value);a.classify(value);
                }
            }
            require(seen.equals(accounts.keySet()), "MISSING_ATTESTATION_REWARD");
            var links = report.putArray("executionLinks");
            String previous = startRoot;
            JsonNode initial = block(io, startHeader, startSlot);
            JsonNode previousPayload = payload(initial);
            links.add(link(io, previousPayload));
            for (long slot = startSlot + 1; slot <= endSlot; slot++) {
                // Missing blocks are INCOMPLETE rather than guessed as zero effects.
                JsonNode h = slot == endSlot ? endHeader : header(io, Long.toString(slot));
                JsonNode message = block(io, h, slot);
                require(previous.equalsIgnoreCase(root(message, "parent_root")), "BEACON_PARENT_MISMATCH");
                JsonNode body = message.path("body");
                for (String name : List.of("deposits", "proposer_slashings", "attester_slashings"))
                    require(array(body, name).isEmpty(), "UNSUPPORTED_" + name.toUpperCase());
                JsonNode requests = body.path("execution_requests");
                for (String name : List.of("deposits", "withdrawals", "consolidations"))
                    require(array(requests, name).isEmpty(), "UNSUPPORTED_EXECUTION_REQUESTS");
                JsonNode p = payload(message);
                require(root(p, "parent_hash").equalsIgnoreCase(root(previousPayload, "block_hash"))
                        && uint(p, "block_number").equals(uint(previousPayload, "block_number").add(BigInteger.ONE)), "EXECUTION_PARENT_MISMATCH");
                links.add(link(io, p));
                for (JsonNode withdrawal : array(p, "withdrawals")) {
                    Account a = accounts.get(uint(withdrawal, "validator_index").toString());
                    if (a != null) a.withdrawals = a.withdrawals.add(uint(withdrawal, "amount"));
                }
                String blockRoot = root(h, "root");
                JsonNode proposer = get(io, "/eth/v1/beacon/rewards/blocks/" + blockRoot); verified(proposer);
                JsonNode reward = proposer.path("data");
                require(uint(reward, "proposer_index").equals(uint(message, "proposer_index")), "PROPOSER_MISMATCH");
                BigInteger total = uint(reward, "total"), sum = BigInteger.ZERO;
                for (String c : List.of("attestations", "sync_aggregate", "proposer_slashings", "attester_slashings")) sum = sum.add(uint(reward, c));
                require(total.equals(sum), "BLOCK_REWARD_TOTAL_MISMATCH");
                Account proposerAccount = accounts.get(uint(reward, "proposer_index").toString());
                if (proposerAccount != null) { proposerAccount.proposer = proposerAccount.proposer.add(total);proposerAccount.classify(total); }
                JsonNode sync = io.beacon(staking.beaconUrl(), "/eth/v1/beacon/rewards/sync_committee/" + blockRoot, ids); verified(sync);
                Set<String> members = new HashSet<>();
                for (JsonNode key : array((slot == endSlot ? end : start).path("current_sync_committee"), "pubkeys")) members.add(key.asText());
                Set<String> expected = new HashSet<>(), actual = new HashSet<>();
                accounts.forEach((id,a) -> { if (members.contains(a.pubkey)) expected.add(id); });
                for (JsonNode r : array(sync, "data")) {
                    String id = uint(r, "validator_index").toString();
                    require(expected.contains(id) && actual.add(id), "SYNC_VALIDATOR_MISMATCH");
                    BigInteger value=signed(r,"reward");accounts.get(id).sync = accounts.get(id).sync.add(value);accounts.get(id).classify(value);
                }
                require(actual.equals(expected), "MISSING_SYNC_REWARD");
                previous = blockRoot; previousPayload = p;
            }
            // Mutable numeric identifiers must still resolve to the same finalized boundaries after collection.
            require(startRoot.equals(root(header(io, Long.toString(startSlot)), "root"))
                    && endRoot.equals(root(header(io, Long.toString(endSlot)), "root")), "CHECKPOINT_CHANGED");
            boolean matched = true;
            var rows = report.putArray("validators");
            for (var e : accounts.entrySet()) {
                Account a = e.getValue();
                BigInteger expected = a.opening.add(a.att).add(a.proposer).add(a.sync).subtract(a.withdrawals);
                BigInteger residual = a.closing.subtract(expected);
                matched &= residual.signum() == 0;
                rows.addObject().put("validatorIndex", e.getKey()).put("openingGwei", a.opening.toString()).put("closingGwei", a.closing.toString())
                        .put("attestationNetGwei", a.att.toString()).put("proposerRewardGwei", a.proposer.toString())
                        .put("syncNetGwei", a.sync.toString()).put("withdrawalsGwei", a.withdrawals.toString())
                        .put("consensusRewardsGwei", a.rewards.toString()).put("consensusPenaltiesGwei", a.penalties.toString())
                        .put("expectedClosingGwei", expected.toString()).put("residualGwei", residual.toString());
            }
            report.put("linkedBlocks", links.size());
            return new Result(matched ? "MATCHED" : "MISMATCH", matched ? null : "BALANCE_RESIDUAL", report, io.raw);
        } catch (Failure f) { return new Result("INCOMPLETE", f.getMessage(), report, io.raw);
        } catch (ArithmeticException | IndexOutOfBoundsException f) { return new Result("INCOMPLETE", "INVALID_CHAIN_VALUE", report, io.raw); }
    }
    private JsonNode get(ChainEvidence io, String path) { return io.beacon(staking.beaconUrl(), path, null); }
    private JsonNode header(ChainEvidence io, String id) {
        JsonNode n = get(io, "/eth/v1/beacon/headers/" + id); verified(n);
        require(n.path("data").path("canonical").isBoolean() && n.path("data").path("canonical").asBoolean(), "NON_CANONICAL");
        return n.path("data");
    }
    private JsonNode state(ChainEvidence io, JsonNode h, long slot) {
        JsonNode n = get(io, "/eth/v2/debug/beacon/states/" + root(h.path("header").path("message"), "state_root")); verified(n);
        require(List.of("electra", "fulu").contains(text(n, "version")), "UNSUPPORTED_FORK");
        JsonNode s = n.path("data");
        require(uint(s, "slot").equals(BigInteger.valueOf(slot)), "STATE_SLOT_MISMATCH");
        require(staking.genesisValidatorsRoot().equalsIgnoreCase(root(s, "genesis_validators_root")), "STATE_NETWORK_MISMATCH");
        require(array(s, "validators").size() <= 4096, "STATE_TOO_LARGE");
        require(array(s, "pending_deposits").isEmpty() && array(s, "pending_consolidations").isEmpty(), "UNSUPPORTED_PENDING_FLOWS");
        return s;
    }
    private JsonNode block(ChainEvidence io, JsonNode h, long slot) {
        JsonNode n = get(io, "/eth/v2/beacon/blocks/" + root(h, "root")); verified(n);
        require(List.of("electra", "fulu").contains(text(n, "version")), "UNSUPPORTED_FORK");
        JsonNode m = n.path("data").path("message"), hm = h.path("header").path("message");
        require(uint(m,"slot").equals(BigInteger.valueOf(slot)) && root(m,"state_root").equals(root(hm,"state_root"))
                && uint(m,"proposer_index").equals(uint(hm,"proposer_index")) && root(m,"parent_root").equals(root(hm,"parent_root")), "BLOCK_HEADER_MISMATCH");
        return m;
    }
    private JsonNode payload(JsonNode message) {
        JsonNode p = message.path("body").path("execution_payload"); require(p.isObject(), "MISSING_EXECUTION_PAYLOAD"); return p;
    }
    private JsonNode link(ChainEvidence io, JsonNode p) {
        String hash = root(p,"block_hash");
        JsonNode block = io.rpc(config.executionUrl(), "eth_getBlockByHash", mapper.createArrayNode().add(hash).add(false));
        String number = "0x" + uint(p,"block_number").toString(16);
        JsonNode canonical = io.rpc(config.executionUrl(), "eth_getBlockByNumber", mapper.createArrayNode().add(number).add(false));
        require(hash.equalsIgnoreCase(root(block,"hash")) && hash.equalsIgnoreCase(root(canonical,"hash")), "EXECUTION_HASH_MISMATCH");
        require(uint(p,"block_number").equals(hex(text(block,"number"))) && uint(p,"timestamp").equals(hex(text(block,"timestamp")))
                && root(p,"parent_hash").equalsIgnoreCase(root(block,"parentHash"))
                && text(p,"fee_recipient").equalsIgnoreCase(text(block,"miner")), "EXECUTION_PAYLOAD_MISMATCH");
        JsonNode cw = array(p,"withdrawals"), ew = array(block,"withdrawals");
        require(cw.size() == ew.size(), "WITHDRAWALS_MISMATCH");
        for (int i=0; i<cw.size(); i++) {
            JsonNode a = cw.get(i), b = ew.get(i);
            require(uint(a,"index").equals(hex(text(b,"index"))) && uint(a,"validator_index").equals(hex(text(b,"validatorIndex")))
                    && uint(a,"amount").equals(hex(text(b,"amount"))) && text(a,"address").equalsIgnoreCase(text(b,"address")), "WITHDRAWALS_MISMATCH");
        }
        return mapper.createObjectNode().put("blockHash",hash).put("blockNumber",uint(p,"block_number").toString());
    }
    private Map<String,Account> accounts(JsonNode start, JsonNode end) {
        JsonNode sv = array(start,"validators"), ev = array(end,"validators"), sb = array(start,"balances"), eb = array(end,"balances");
        require(sv.size()==sb.size() && ev.size()==eb.size(), "BALANCE_REGISTRY_MISMATCH");
        Map<String,Account> result = new LinkedHashMap<>();
        for (String configured : staking.validatorIds()) {
            int index = -1;
            if (configured.startsWith("0x")) { for(int i=0;i<sv.size();i++) if(configured.equalsIgnoreCase(text(sv.get(i),"pubkey"))) index=i; }
            else { try { index = Integer.parseInt(configured); } catch(NumberFormatException e) { throw new Failure("VALIDATOR_NOT_FOUND"); } }
            require(index>=0 && index<sv.size() && index<ev.size(), "VALIDATOR_NOT_FOUND");
            JsonNode a=sv.get(index), b=ev.get(index);
            require(text(a,"pubkey").equals(text(b,"pubkey")), "VALIDATOR_IDENTITY_CHANGED");
            for(JsonNode v:List.of(a,b)) {
                require(v.path("slashed").isBoolean() && !v.get("slashed").asBoolean(), "UNSUPPORTED_SLASHED_VALIDATOR");
                require(uint(v,"exit_epoch").equals(new BigInteger("18446744073709551615")), "UNSUPPORTED_EXITING_VALIDATOR");
            }
            var opening = mapper.createObjectNode().set("balance",sb.get(index));
            var closing = mapper.createObjectNode().set("balance",eb.get(index));
            require(result.put(Integer.toString(index),new Account(text(a,"pubkey"),uint(opening,"balance"),uint(closing,"balance")))==null,"DUPLICATE_VALIDATOR");
        }
        return result;
    }
    private static class Account {
        final String pubkey; final BigInteger opening, closing;
        BigInteger att=BigInteger.ZERO, proposer=BigInteger.ZERO, sync=BigInteger.ZERO, withdrawals=BigInteger.ZERO, rewards=BigInteger.ZERO, penalties=BigInteger.ZERO;
        void classify(BigInteger value) { if(value.signum()<0) penalties=penalties.subtract(value); else rewards=rewards.add(value); }
        Account(String pubkey,BigInteger opening,BigInteger closing){this.pubkey=pubkey;this.opening=opening;this.closing=closing;}
    }
}
