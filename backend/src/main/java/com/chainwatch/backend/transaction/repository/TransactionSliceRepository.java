package com.chainwatch.backend.transaction.repository;

import com.chainwatch.backend.transaction.domain.Transaction;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

/** 거래 목록을 전체 count 쿼리 없이 조회하는 repository fragment. */
public interface TransactionSliceRepository {

    Slice<Transaction> searchSlice(
            String wallet,
            Long blockNumber,
            Instant from,
            Instant to,
            Pageable pageable
    );
}
