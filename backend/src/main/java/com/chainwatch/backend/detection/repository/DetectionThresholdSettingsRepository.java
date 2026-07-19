package com.chainwatch.backend.detection.repository;

import com.chainwatch.backend.detection.domain.DetectionThresholdSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetectionThresholdSettingsRepository extends JpaRepository<DetectionThresholdSettings, Long> {
}
