package com.chainwatch.backend.staking;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
public interface StakingObservationRepository extends JpaRepository<StakingObservation, Long> {
    List<StakingObservation> findByNetworkAndGenesisRootOrderByStartedAtDescIdDesc(String network, String genesisRoot, Pageable page);
}
