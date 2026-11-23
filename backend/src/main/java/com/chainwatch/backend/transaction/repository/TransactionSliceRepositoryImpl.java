package com.chainwatch.backend.transaction.repository;

import com.chainwatch.backend.transaction.domain.Transaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Repository;

@Repository
public class TransactionSliceRepositoryImpl implements TransactionSliceRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Slice<Transaction> searchSlice(
            String wallet,
            Long blockNumber,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Transaction> query = cb.createQuery(Transaction.class);
        Root<Transaction> root = query.from(Transaction.class);

        Predicate predicate = TransactionSpecifications
                .search(wallet, blockNumber, from, to)
                .toPredicate(root, query, cb);
        query.where(predicate);

        List<Order> orders = new ArrayList<>();
        pageable.getSort().forEach(sort -> orders.add(sort.isAscending()
                ? cb.asc(root.get(sort.getProperty()))
                : cb.desc(root.get(sort.getProperty()))));
        query.orderBy(orders);

        int pageSize = pageable.getPageSize();
        TypedQuery<Transaction> typedQuery = entityManager.createQuery(query)
                .setFirstResult(Math.toIntExact(pageable.getOffset()))
                .setMaxResults(pageSize + 1);
        List<Transaction> rows = typedQuery.getResultList();
        boolean hasNext = rows.size() > pageSize;
        if (hasNext) {
            rows = new ArrayList<>(rows.subList(0, pageSize));
        }
        return new SliceImpl<>(rows, pageable, hasNext);
    }
}
