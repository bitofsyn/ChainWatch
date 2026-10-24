package com.chainwatch.backend.staking.reconciliation;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReconciliationRepository extends JpaRepository<ReconciliationRun,Long> {
    List<ReconciliationRun> findByNetworkAndGenesisRootOrderByStartedAtDescIdDesc(String network,String genesisRoot,Pageable page);
}
