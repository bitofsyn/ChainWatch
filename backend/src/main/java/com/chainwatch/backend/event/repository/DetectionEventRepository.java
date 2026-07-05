package com.chainwatch.backend.event.repository;

import com.chainwatch.backend.event.domain.DetectionEvent;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DetectionEventRepository
        extends JpaRepository<DetectionEvent, Long>, JpaSpecificationExecutor<DetectionEvent> {

    boolean existsByTransactionIdAndEventType(Long transactionId, EventType eventType);

    @Override
    @EntityGraph(attributePaths = "transaction")
    Optional<DetectionEvent> findById(Long id);

    @Override
    @EntityGraph(attributePaths = "transaction")
    Page<DetectionEvent> findAll(Specification<DetectionEvent> spec, Pageable pageable);

    default Page<DetectionEvent> search(
            EventType eventType,
            RiskLevel riskLevel,
            String wallet,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        return findAll(DetectionEventSpecifications.search(eventType, riskLevel, wallet, from, to), pageable);
    }
}
