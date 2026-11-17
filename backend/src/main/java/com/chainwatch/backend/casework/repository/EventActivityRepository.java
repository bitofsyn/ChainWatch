package com.chainwatch.backend.casework.repository;

import com.chainwatch.backend.casework.domain.EventActivity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventActivityRepository extends JpaRepository<EventActivity, Long> {

    List<EventActivity> findByEventIdOrderByCreatedAtDescIdDesc(Long eventId);
}
