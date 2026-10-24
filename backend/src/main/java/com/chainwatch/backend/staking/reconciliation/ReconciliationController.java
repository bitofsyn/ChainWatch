package com.chainwatch.backend.staking.reconciliation;
import com.chainwatch.backend.staking.StakingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestController
@RequestMapping("/api/ops/staking/reconciliations")
public class ReconciliationController {
    private final StakingProperties staking; private final ReconciliationProperties config;
    private final EpochReconciliationClient client; private final ReconciliationStore store;
    private final ReconciliationRepository repository; private final ObjectMapper mapper;
    private final AtomicBoolean running=new AtomicBoolean();
    public ReconciliationController(StakingProperties staking,ReconciliationProperties config,EpochReconciliationClient client,
            ReconciliationStore store,ReconciliationRepository repository,ObjectMapper mapper){
        this.staking=staking;this.config=config;this.client=client;this.store=store;this.repository=repository;this.mapper=mapper;
    }
    public record Request(JsonNode epoch){}
    public record View(Long id,String network,long accountingEpoch,String status,String errorCode,Instant startedAt,Instant completedAt,JsonNode report){}
    public record Overview(boolean enabled,List<View> runs){}
    @GetMapping public Overview list(){return new Overview(config.enabled() && staking.enabled(),repository.findByNetworkAndGenesisRootOrderByStartedAtDescIdDesc(
        staking.network(),staking.genesisValidatorsRoot(),PageRequest.of(0,20)).stream().map(this::view).toList());}
    @PostMapping public View run(@RequestBody Request request){
        if(request.epoch()==null || !request.epoch().isIntegralNumber() || !request.epoch().canConvertToLong())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Epoch must be an integer");
        long epoch=request.epoch().longValue();
        if(epoch<1 || epoch>1_000_000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Epoch must be 1..1000000");
        if(!config.enabled() || !staking.enabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Reconciliation disabled");
        if(!running.compareAndSet(false,true)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Reconciliation running");
        try {Long id=store.start(staking,epoch);return view(store.complete(id,client.reconcile(epoch)));}
        finally{running.set(false);}
    }
    @GetMapping("/{id}/evidence") public JsonNode evidence(@PathVariable Long id){
        var r=repository.findById(id).filter(x->x.getNetwork().equals(staking.network()) && x.getGenesisRoot().equals(staking.genesisValidatorsRoot()))
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));return json(r.getEvidenceJson());
    }
    private View view(ReconciliationRun r){return new View(r.getId(),r.getNetwork(),r.getAccountingEpoch(),r.getStatus(),r.getErrorCode(),r.getStartedAt(),r.getCompletedAt(),json(r.getReportJson()));}
    private JsonNode json(String s){try{return s==null?mapper.nullNode():mapper.readTree(s);}catch(java.io.IOException e){throw new IllegalStateException(e);}}
}
