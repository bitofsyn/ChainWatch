package com.chainwatch.backend.staking.reconciliation;

import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name="staking_reconciliations", indexes=@Index(name="idx_reconciliation_network", columnList="network,genesis_root,started_at"))
public class ReconciliationRun {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=80) private String network;
    @Column(nullable=false,length=66) private String genesisRoot;
    @Column(nullable=false) private long accountingEpoch;
    @Column(nullable=false,length=20) private String status;
    @Column(nullable=false) private Instant startedAt;
    private Instant completedAt;
    @Column(length=80) private String errorCode;
    @Column(columnDefinition="text") private String reportJson;
    @Column(columnDefinition="text") private String evidenceJson;
    protected ReconciliationRun() {}
    ReconciliationRun(String network,String genesisRoot,long epoch) {
        this.network=network;this.genesisRoot=genesisRoot;this.accountingEpoch=epoch;status="RUNNING";startedAt=Instant.now();
    }
    void complete(EpochReconciliationClient.Result r) {
        if(!"RUNNING".equals(status)) throw new IllegalStateException("Attempt already completed");
        status=r.status();errorCode=r.errorCode();reportJson=r.report().toString();evidenceJson=r.evidence().toString();completedAt=Instant.now();
    }
    public Long getId(){return id;} public String getNetwork(){return network;} public String getGenesisRoot(){return genesisRoot;}
    public long getAccountingEpoch(){return accountingEpoch;} public String getStatus(){return status;}
    public Instant getStartedAt(){return startedAt;} public Instant getCompletedAt(){return completedAt;}
    public String getErrorCode(){return errorCode;} public String getReportJson(){return reportJson;} public String getEvidenceJson(){return evidenceJson;}
}
