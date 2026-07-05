package com.chainwatch.backend.event.repository;

import com.chainwatch.backend.event.domain.DetectionEvent;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** null 파라미터는 조건 자체를 생성하지 않아 DB 타입 추론 문제를 원천 차단한다. */
public final class DetectionEventSpecifications {

    private DetectionEventSpecifications() {
    }

    public static Specification<DetectionEvent> search(
            EventType eventType,
            RiskLevel riskLevel,
            String wallet,
            Instant from,
            Instant to
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (eventType != null) {
                predicates.add(cb.equal(root.get("eventType"), eventType));
            }
            if (riskLevel != null) {
                predicates.add(cb.equal(root.get("riskLevel"), riskLevel));
            }
            if (wallet != null && !wallet.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("walletAddress")), wallet.toLowerCase()));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("detectedAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("detectedAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
