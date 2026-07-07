package com.chainwatch.backend.notification.repository;

import com.chainwatch.backend.notification.domain.NotificationHistory;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, Long> {

    Page<NotificationHistory> findAllByOrderBySentAtDesc(Pageable pageable);

    Page<NotificationHistory> findByEventIdOrderBySentAtDesc(Long eventId, Pageable pageable);

    long countBySuccessFalseAndSentAtAfter(Instant threshold);
}
