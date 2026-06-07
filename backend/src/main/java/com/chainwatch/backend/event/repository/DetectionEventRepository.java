package com.chainwatch.backend.event.repository;

import com.chainwatch.backend.event.domain.DetectionEvent;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DetectionEventRepository extends JpaRepository<DetectionEvent, Long> {
    boolean existsByTransactionIdAndEventType(Long transactionId, EventType eventType);

    @Query("""
            select e
            from DetectionEvent e
            where (:eventType is null or e.eventType = :eventType)
              and (:riskLevel is null or e.riskLevel = :riskLevel)
              and (:wallet is null or lower(e.walletAddress) = lower(:wallet))
              and (:from is null or e.detectedAt >= :from)
              and (:to is null or e.detectedAt <= :to)
            """)
    Page<DetectionEvent> search(
            @Param("eventType") EventType eventType,
            @Param("riskLevel") RiskLevel riskLevel,
            @Param("wallet") String wallet,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );
}
