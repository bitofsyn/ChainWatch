package com.chainwatch.backend.transaction.repository;

import com.chainwatch.backend.transaction.domain.Transaction;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** null 파라미터는 조건 자체를 생성하지 않아 DB 타입 추론 문제를 원천 차단한다. */
public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> search(
            String wallet,
            Long blockNumber,
            Instant from,
            Instant to
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (wallet != null && !wallet.isBlank()) {
                String normalized = wallet.toLowerCase();
                predicates.add(cb.or(
                        cb.equal(cb.lower(root.get("fromAddress")), normalized),
                        cb.equal(cb.lower(root.get("toAddress")), normalized)
                ));
            }
            if (blockNumber != null) {
                predicates.add(cb.equal(root.get("blockNumber"), blockNumber));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
