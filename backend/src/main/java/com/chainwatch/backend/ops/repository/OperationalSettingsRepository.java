package com.chainwatch.backend.ops.repository;

import com.chainwatch.backend.ops.domain.OperationalSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationalSettingsRepository extends JpaRepository<OperationalSettings, Long> {}
