package com.chainwatch.backend.event.repository;

import com.chainwatch.backend.event.domain.DetectionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetectionEventRepository extends JpaRepository<DetectionEvent, Long> {
}
