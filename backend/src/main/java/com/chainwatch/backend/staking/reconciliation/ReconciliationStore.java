package com.chainwatch.backend.staking.reconciliation;
import com.chainwatch.backend.audit.service.AuditLogService;
import com.chainwatch.backend.staking.StakingProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ReconciliationStore {
    private final ReconciliationRepository repository;
    private final AuditLogService audit;
    public ReconciliationStore(ReconciliationRepository repository,AuditLogService audit){this.repository=repository;this.audit=audit;}
    @Transactional public Long start(StakingProperties p,long epoch){
        var r=repository.save(new ReconciliationRun(p.network(),p.genesisValidatorsRoot(),epoch));
        audit.record("STAKING_RECONCILIATION_STARTED","STAKING_RECONCILIATION",r.getId().toString(),"epoch="+epoch);return r.getId();
    }
    @Transactional public ReconciliationRun complete(Long id,EpochReconciliationClient.Result result){
        var r=repository.findById(id).orElseThrow();r.complete(result);
        audit.record("STAKING_RECONCILIATION_COMPLETED","STAKING_RECONCILIATION",id.toString(),r.getStatus());return r;
    }
}
