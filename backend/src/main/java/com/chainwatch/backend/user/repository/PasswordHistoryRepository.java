package com.chainwatch.backend.user.repository;

import com.chainwatch.backend.user.domain.PasswordHistory;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {
    @Query("select h from PasswordHistory h where h.userId = :userId and h.endedAt is not null order by h.endedAt desc, h.id desc")
    List<PasswordHistory> findRecentEnded(@Param("userId") Long userId, Pageable page);

    @Modifying
    @Query("update PasswordHistory h set h.endedAt = :endedAt where h.userId = :userId and h.endedAt is null")
    int endCurrent(@Param("userId") Long userId, @Param("endedAt") Instant endedAt);
}
