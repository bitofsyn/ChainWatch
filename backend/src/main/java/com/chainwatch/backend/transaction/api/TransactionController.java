package com.chainwatch.backend.transaction.api;

import com.chainwatch.backend.collector.service.ChainFinalityService;
import com.chainwatch.backend.common.exception.ResourceNotFoundException;
import com.chainwatch.backend.transaction.domain.Transaction;
import com.chainwatch.backend.transaction.repository.TransactionRepository;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private static final int MAX_PAGE_SIZE = 100;

    private final TransactionRepository transactionRepository;
    private final ChainFinalityService chainFinalityService;

    public TransactionController(
            TransactionRepository transactionRepository,
            ChainFinalityService chainFinalityService
    ) {
        this.transactionRepository = transactionRepository;
        this.chainFinalityService = chainFinalityService;
    }

    @GetMapping
    public Page<TransactionResponse> getTransactions(
            @RequestParam(required = false) String wallet,
            @RequestParam(required = false) Long blockNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "timestamp"));
        // head는 요청당 한 번만 조회하고 페이지 요소들의 confirmations 계산에 재사용한다.
        Long chainHead = chainFinalityService.lastKnownChainHead().orElse(null);
        return transactionRepository.search(wallet, blockNumber, from, to, pageable)
                .map(transaction -> TransactionResponse.from(
                        transaction,
                        chainFinalityService.confirmationFor(transaction.getBlockNumber(), chainHead)
                ));
    }

    @GetMapping("/{id}")
    public TransactionResponse getTransaction(@PathVariable Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + id));
        Long chainHead = chainFinalityService.lastKnownChainHead().orElse(null);
        return TransactionResponse.from(
                transaction,
                chainFinalityService.confirmationFor(transaction.getBlockNumber(), chainHead)
        );
    }
}
