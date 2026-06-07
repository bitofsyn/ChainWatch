package com.chainwatch.backend.collector.repository;

import com.chainwatch.backend.collector.domain.CollectorState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectorStateRepository extends JpaRepository<CollectorState, String> {
}
