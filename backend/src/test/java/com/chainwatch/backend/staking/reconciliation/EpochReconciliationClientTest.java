package com.chainwatch.backend.staking.reconciliation;

import static org.assertj.core.api.Assertions.*;
import com.chainwatch.backend.staking.StakingProperties;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.time.Duration;
import java.util.List;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.*;

class EpochReconciliationClientTest {
    ObjectMapper m=new ObjectMapper(); MockWebServer server; String fault="";
    static String h(long n){return "0x"+String.format("%064x",n);}
    static final String KEY="0x"+"a".repeat(96), ADDRESS="0x"+"b".repeat(40);
    @BeforeEach void setup() throws Exception {
        server=new MockWebServer();server.setDispatcher(new Dispatcher(){@Override public MockResponse dispatch(RecordedRequest r){
            try { if (fault.equals("missing-block") && r.getPath().equals("/eth/v1/beacon/headers/12")) return new MockResponse().setResponseCode(404);
                return new MockResponse().setHeader("Content-Type","application/json").setBody(reply(r).toString());
            }catch(Exception e){return new MockResponse().setResponseCode(500).setBody(e.toString());}
        }});server.start();
    }
    @AfterEach void close() throws Exception{server.shutdown();}
    EpochReconciliationClient.Result run(){
        var p=new StakingProperties(true,"local",server.url("/").uri(),h(999),List.of("0"),Duration.ofSeconds(2),null,null);
        return new EpochReconciliationClient(p,new ReconciliationProperties(true,server.url("/").uri()),m).reconcile(1);
    }
    ObjectNode object(){return m.createObjectNode();}
    ObjectNode wrap(JsonNode data){return object().put("execution_optimistic",false).put("finalized",true).set("data",data);}
    ObjectNode header(long slot){return object().put("slot",""+slot).put("proposer_index","0").put("parent_root",h(slot-1)).put("state_root",h(1000+slot));}
    ObjectNode withdrawal(long slot,boolean hex){return object().put("index",hex?"0x0":"0").put(hex?"validatorIndex":"validator_index",hex?"0x0":"0").put("amount",hex?"0x2":"2").put("address",ADDRESS);}
    ArrayNode withdrawals(long slot,boolean hex){var a=m.createArrayNode();if(slot==12)a.add(withdrawal(slot,hex));return a;}
    JsonNode reply(RecordedRequest request) throws Exception {
        String path=request.getPath(), id=path.substring(path.lastIndexOf('/')+1);
        if(path.equals("/")){
            JsonNode r=m.readTree(request.getBody().readUtf8());String method=r.get("method").asText();JsonNode result;
            if(method.equals("eth_chainId"))result=m.getNodeFactory().textNode(fault.equals("wrong-network")?"0x2":"0x1");
            else if(method.equals("web3_clientVersion"))result=m.getNodeFactory().textNode("fixture-geth");
            else {
                long slot=Long.parseLong(r.path("params").get(0).asText().substring(2),16);
                if(method.equals("eth_getBlockByHash"))slot-=2000;
                var b=object().put("hash",fault.equals("wrong-hash")?h(99999):h(2000+slot)).put("number","0x"+Long.toHexString(slot))
                    .put("timestamp","0x"+Long.toHexString(slot*6)).put("parentHash",h(1999+slot)).put("miner",ADDRESS);
                b.set("withdrawals",withdrawals(slot,true));result=b;
            }
            return object().put("jsonrpc","2.0").put("id",1).set("result",result);
        }
        if(path.endsWith("genesis"))return wrap(object().put("genesis_validators_root",h(999)).put("genesis_time","0"));
        if(path.endsWith("spec"))return wrap(object().put("PRESET_BASE",fault.equals("mainnet")?"mainnet":"minimal").put("SLOTS_PER_EPOCH","8").put("DEPOSIT_CHAIN_ID","1"));
        if(path.endsWith("version"))return wrap(object().put("version","fixture-teku"));
        if(path.contains("/headers/")) {
            long slot=id.equals("finalized")?(fault.equals("unfinalized")?8:24):Long.parseLong(id);
            return wrap(object().put("root",h(slot)).put("canonical",true).set("header",object().set("message",header(slot))));
        }
        if(path.contains("/debug/beacon/states/")){
            long slot=Long.parseLong(id.substring(2),16)-1000;
            var state=object().put("slot",""+slot).put("genesis_validators_root",h(999)).put("genesis_time","0");
            state.set("fork",object().put("current_version","0x70000038"));
            state.putArray("validators").add(object().put("pubkey",KEY).put("slashed",false).put("exit_epoch","18446744073709551615"));
            state.putArray("balances").add(slot==8?"100":fault.equals("residual")?"110":"109");
            state.putArray("pending_deposits");state.putArray("pending_consolidations");
            if(fault.equals("pending-deposit"))((ArrayNode)state.get("pending_deposits")).add(object());
            state.set("current_sync_committee",object().set("pubkeys",m.createArrayNode().add(KEY)));
            return wrap(state).put("version","fulu");
        }
        if(path.contains("/rewards/attestations/")){
            assertThat(id).isEqualTo("0");
            var data=object();var list=data.putArray("total_rewards");
            if(!fault.equals("missing-attestation"))list.add(object().put("validator_index","0").put("head","3").put("target","2").put("source","-1").put("inactivity","-1"));
            return wrap(data);
        }
        if(path.contains("/rewards/sync_committee/"))return wrap(fault.equals("missing-sync")?m.createArrayNode():m.createArrayNode().add(object().put("validator_index","0").put("reward","-1")));
        if(path.contains("/rewards/blocks/"))return wrap(object().put("proposer_index","0").put("total","2").put("attestations","2").put("sync_aggregate","0").put("proposer_slashings","0").put("attester_slashings","0"));
        if(path.contains("/beacon/blocks/")){
            long slot=Long.parseLong(id.substring(2),16);var body=object();
            for(String f:List.of("deposits","proposer_slashings","attester_slashings"))body.putArray(f);
            var req=body.putObject("execution_requests");for(String f:List.of("deposits","withdrawals","consolidations"))req.putArray(f);
            var payload=body.putObject("execution_payload").put("block_hash",h(2000+slot)).put("parent_hash",h(1999+slot)).put("block_number",""+slot).put("timestamp",""+(slot*6)).put("fee_recipient",ADDRESS);
            payload.set("withdrawals",withdrawals(slot,false));
            return wrap(object().set("message",header(slot).set("body",body))).put("version","fulu");
        }
        throw new IllegalArgumentException(path);
    }
    @Test void reconcilesNegativeRewardsAndWithdrawalsWithoutDoubleCounting(){
        var r=run();assertThat(r.status()).isEqualTo("MATCHED");
        var row=r.report().get("validators").get(0);
        assertThat(row.get("attestationNetGwei").asText()).isEqualTo("3");
        assertThat(row.get("syncNetGwei").asText()).isEqualTo("-8");
        assertThat(row.get("proposerRewardGwei").asText()).isEqualTo("16");
        assertThat(row.get("consensusRewardsGwei").asText()).isEqualTo("21");
        assertThat(row.get("consensusPenaltiesGwei").asText()).isEqualTo("10");
        assertThat(row.get("withdrawalsGwei").asText()).isEqualTo("2");
        assertThat(row.get("residualGwei").asText()).isEqualTo("0");
        assertThat(r.report().get("linkedBlocks").asInt()).isEqualTo(9);
        assertThat(r.report().get("settlementEligible").asBoolean()).isFalse();
    }
    @Test void unexplainedDeltaIsMismatchNotInventedReward(){fault="residual";var r=run();assertThat(r.status()).isEqualTo("MISMATCH");assertThat(r.report().path("validators").get(0).path("residualGwei").asText()).isEqualTo("1");}
    @Test void wrongExecutionHashRetainsPartialEvidence(){fault="wrong-hash";var r=run();assertThat(r.errorCode()).isEqualTo("EXECUTION_HASH_MISMATCH");assertThat(r.status()).isEqualTo("INCOMPLETE");assertThat(r.evidence().size()).isGreaterThan(5);}
    @Test void missingBlockIsNotZeroEffects(){fault="missing-block";assertThat(run().errorCode()).isEqualTo("UPSTREAM_HTTP_404");}
    @Test void missingRewardIsNotZero(){fault="missing-attestation";assertThat(run().errorCode()).isEqualTo("MISSING_ATTESTATION_REWARD");}
    @Test void missingSyncMemberIsNotZero(){fault="missing-sync";assertThat(run().errorCode()).isEqualTo("MISSING_SYNC_REWARD");}
    @Test void pendingDepositsAreExplicitlyUnsupported(){fault="pending-deposit";assertThat(run().errorCode()).isEqualTo("UNSUPPORTED_PENDING_FLOWS");}
    @Test void unfinalizedEpochIsBlocked(){fault="unfinalized";assertThat(run().errorCode()).isEqualTo("EPOCH_NOT_FINALIZED");}
    @Test void wrongExecutionChainIsBlocked(){fault="wrong-network";assertThat(run().errorCode()).isEqualTo("EXECUTION_NETWORK_MISMATCH");}
    @Test void mainnetFullStateIsNeverDownloaded(){fault="mainnet";assertThat(run().errorCode()).isEqualTo("UNSUPPORTED_PRESET");assertThat(server.getRequestCount()).isEqualTo(2);}
}
