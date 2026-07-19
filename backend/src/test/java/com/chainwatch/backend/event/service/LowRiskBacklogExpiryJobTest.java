package com.chainwatch.backend.event.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chainwatch.backend.event.domain.DetectionEvent;
import com.chainwatch.backend.event.domain.EventStatus;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import com.chainwatch.backend.event.repository.DetectionEventRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 저위험 backlog 자동 만료 검증: 보존 기간(기본 3일)이 지난 MEDIUM/LOW NEW(레거시 null 포함)만
 * RESOLVED + 사유로 종결되고, CRITICAL/HIGH·최근 이벤트·처리 중 이벤트는 건드리지 않는다.
 */
@SpringBootTest
class LowRiskBacklogExpiryJobTest {

    @Autowired
    private LowRiskBacklogExpiryJob expiryJob;

    @Autowired
    private DetectionEventRepository detectionEventRepository;

    @AfterEach
    void cleanUp() {
        detectionEventRepository.deleteAll();
    }

    @Test
    void expiresOnlyStaleLowRiskNewEvents() {
        Instant now = Instant.now();
        DetectionEvent staleMedium = saveEvent(RiskLevel.MEDIUM, now.minus(Duration.ofDays(5)), null);
        DetectionEvent staleMediumNew =
                saveEvent(RiskLevel.MEDIUM, now.minus(Duration.ofDays(4)), EventStatus.NEW);
        DetectionEvent recentMedium =
                saveEvent(RiskLevel.MEDIUM, now.minus(Duration.ofHours(1)), EventStatus.NEW);
        DetectionEvent staleHigh = saveEvent(RiskLevel.HIGH, now.minus(Duration.ofDays(5)), EventStatus.NEW);
        DetectionEvent staleInvestigating =
                saveEvent(RiskLevel.MEDIUM, now.minus(Duration.ofDays(5)), EventStatus.INVESTIGATING);

        int expired = expiryJob.expireNow(now);

        assertThat(expired).isEqualTo(2);
        DetectionEvent reloadedStale = detectionEventRepository.findById(staleMedium.getId()).orElseThrow();
        assertThat(reloadedStale.getStatus()).isEqualTo(EventStatus.RESOLVED);
        assertThat(reloadedStale.getResolutionReason())
                .startsWith(LowRiskBacklogExpiryJob.RESOLUTION_REASON_PREFIX);
        // DB 타임스탬프 정밀도(마이크로초) 반올림을 감안한 근사 비교
        assertThat(reloadedStale.getStatusChangedAt())
                .isBetween(now.minusSeconds(1), now.plusSeconds(1));
        assertThat(statusOf(staleMediumNew)).isEqualTo(EventStatus.RESOLVED);
        // 최근 저위험, 고위험, 처리 진행 중 이벤트는 그대로다.
        assertThat(statusOf(recentMedium)).isEqualTo(EventStatus.NEW);
        assertThat(statusOf(staleHigh)).isEqualTo(EventStatus.NEW);
        assertThat(statusOf(staleInvestigating)).isEqualTo(EventStatus.INVESTIGATING);
    }

    @Test
    void secondRunIsIdempotent() {
        Instant now = Instant.now();
        saveEvent(RiskLevel.MEDIUM, now.minus(Duration.ofDays(5)), EventStatus.NEW);

        assertThat(expiryJob.expireNow(now)).isEqualTo(1);
        assertThat(expiryJob.expireNow(now)).isEqualTo(0);
    }

    private EventStatus statusOf(DetectionEvent event) {
        return detectionEventRepository.findById(event.getId()).orElseThrow().getStatus();
    }

    private DetectionEvent saveEvent(RiskLevel riskLevel, Instant detectedAt, EventStatus status) {
        DetectionEvent event = new DetectionEvent(
                EventType.RAPID_TRANSFER,
                riskLevel,
                70,
                "expiry test event",
                "0xwallet",
                detectedAt,
                null
        );
        if (status != null) {
            event.changeStatus(status);
        }
        return detectionEventRepository.save(event);
    }
}
