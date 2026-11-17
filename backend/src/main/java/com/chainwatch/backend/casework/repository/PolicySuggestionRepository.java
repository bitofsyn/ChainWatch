package com.chainwatch.backend.casework.repository;

import com.chainwatch.backend.casework.domain.PolicySuggestion;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicySuggestionRepository extends JpaRepository<PolicySuggestion, Long> {

    List<PolicySuggestion> findTop200ByOrderByCreatedAtDesc();

    List<PolicySuggestion> findByStatus(String status);

    boolean existsByAddressAndStatusIn(String address, Collection<String> statuses);
}
