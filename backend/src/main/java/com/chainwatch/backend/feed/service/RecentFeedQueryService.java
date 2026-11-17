package com.chainwatch.backend.feed.service;

import com.chainwatch.backend.event.repository.DetectionEventRepository;
import com.chainwatch.backend.messaging.producer.CollectedTransactionMessage;
import com.chainwatch.backend.messaging.producer.DetectedEventMessage;
import com.chainwatch.backend.transaction.repository.TransactionRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Redis 피드 캐시가 없는 구성에서 "최근 수집/탐지 내역"을 DB에서 직접 읽는다.
 * 캐시와 같은 응답 형식을 돌려주므로 화면은 구성 차이를 알 필요가 없다.
 */
@Service
public class RecentFeedQueryService {

    private static final int MAX_LIMIT = 100;

    private final TransactionRepository transactionRepository;
    private final DetectionEventRepository detectionEventRepository;

    public RecentFeedQueryService(
            TransactionRepository transactionRepository,
            DetectionEventRepository detectionEventRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.detectionEventRepository = detectionEventRepository;
    }

    @Transactional(readOnly = true)
    public List<CollectedTransactionMessage> recentTransactions(int limit) {
        return transactionRepository.findAll(latest(limit)).stream()
                .map(CollectedTransactionMessage::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DetectedEventMessage> recentEvents(int limit) {
        return detectionEventRepository.findAll(latest(limit)).stream()
                .map(DetectedEventMessage::from)
                .toList();
    }

    private static PageRequest latest(int limit) {
        return PageRequest.of(0, Math.min(Math.max(1, limit), MAX_LIMIT), Sort.by(Sort.Direction.DESC, "id"));
    }
}
