package com.chainwatch.backend.notification.repository;

import com.chainwatch.backend.notification.domain.NotificationHistory;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, Long> {

    /** 최근 발송 성공 건의 실측 소요 시간 평균(ms). 표본이 없으면 null. */
    @Query("""
            select avg(h.durationMs) from NotificationHistory h
            where h.success = true and h.sentAt > :threshold and h.durationMs is not null
            """)
    Double averageDurationMs(@Param("threshold") Instant threshold);

    Page<NotificationHistory> findAllByOrderBySentAtDesc(Pageable pageable);

    Page<NotificationHistory> findByEventIdOrderBySentAtDesc(Long eventId, Pageable pageable);

    long countBySuccessFalseAndSentAtAfter(Instant threshold);

    long countBySuccessTrueAndSentAtAfter(Instant threshold);

    List<NotificationHistory> findTop6ByOrderBySentAtDesc();

    List<NotificationHistory> findTop4BySuccessFalseOrderBySentAtDesc();

    @Modifying
    long deleteByChannel(String channel);
}
