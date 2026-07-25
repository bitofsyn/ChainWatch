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
 * 저위험 중복 이벤트 자동 트리아지 검증: 같은 지갑·같은 유형의 더 최신 미처리 이벤트가 있는
 * MEDIUM/LOW NEW(레거시 null 포함)만 RESOLVED + 사유로 종결되고, 최신 대표·단일 이벤트·
 * CRITICAL/HIGH·처리 중 대표만 남은 경우는 건드리지 않는다.
 */
@SpringBootTest
class DuplicateEventTriageJobTest {

    @Autowired
    private DuplicateEventTriageJob triageJob;

    @Autowired
    private DetectionEventRepository detectionEventRepository;

    @AfterEach
    void cleanUp() {
        detectionEventRepository.deleteAll();
    }

    @Test
    void resolvesOlderDuplicatesKeepingNewestPerWalletAndType() {
        Instant now = Instant.now();
        // w1 RAPID_TRANSFER 3건: 옛 2건(NEW/레거시 null)은 종결, 최신 1건만 남는다.
        DetectionEvent w1Old = saveEvent("0xw1", EventType.RAPID_TRANSFER, RiskLevel.MEDIUM,
                now.minus(Duration.ofHours(3)), EventStatus.NEW);
        DetectionEvent w1Legacy = saveEvent("0xw1", EventType.RAPID_TRANSFER, RiskLevel.LOW,
                now.minus(Duration.ofHours(2)), null);
        DetectionEvent w1Newest = saveEvent("0xw1", EventType.RAPID_TRANSFER, RiskLevel.MEDIUM,
                now.minus(Duration.ofHours(1)), EventStatus.NEW);
        // 같은 지갑이라도 유형이 다르면 별개 사안이다.
        DetectionEvent w1FanOut = saveEvent("0xw1", EventType.FAN_OUT, RiskLevel.MEDIUM,
                now.minus(Duration.ofHours(2)), EventStatus.NEW);
        // 다른 지갑 단일 이벤트는 그대로다.
        DetectionEvent w2Single = saveEvent("0xw2", EventType.RAPID_TRANSFER, RiskLevel.MEDIUM,
                now.minus(Duration.ofHours(2)), EventStatus.NEW);
        // 고위험은 중복이어도 절대 대상이 아니다.
        DetectionEvent w3HighOld = saveEvent("0xw3", EventType.RAPID_TRANSFER, RiskLevel.HIGH,
                now.minus(Duration.ofHours(3)), EventStatus.NEW);
        DetectionEvent w3HighNew = saveEvent("0xw3", EventType.RAPID_TRANSFER, RiskLevel.HIGH,
                now.minus(Duration.ofHours(1)), EventStatus.NEW);
        // 더 최신 건이 이미 처리 중이면(NEW 아님) 살아있는 대표가 없으므로 옛 건을 접지 않는다.
        DetectionEvent w4Old = saveEvent("0xw4", EventType.RAPID_TRANSFER, RiskLevel.MEDIUM,
                now.minus(Duration.ofHours(3)), EventStatus.NEW);
        DetectionEvent w4Investigating = saveEvent("0xw4", EventType.RAPID_TRANSFER, RiskLevel.MEDIUM,
                now.minus(Duration.ofHours(1)), EventStatus.INVESTIGATING);

        int triaged = triageJob.triageNow(now);

        assertThat(triaged).isEqualTo(2);
        DetectionEvent reloadedOld = detectionEventRepository.findById(w1Old.getId()).orElseThrow();
        assertThat(reloadedOld.getStatus()).isEqualTo(EventStatus.RESOLVED);
        assertThat(reloadedOld.getResolutionReason()).isEqualTo(DuplicateEventTriageJob.RESOLUTION_REASON);
        assertThat(statusOf(w1Legacy)).isEqualTo(EventStatus.RESOLVED);
        assertThat(statusOf(w1Newest)).isEqualTo(EventStatus.NEW);
        assertThat(statusOf(w1FanOut)).isEqualTo(EventStatus.NEW);
        assertThat(statusOf(w2Single)).isEqualTo(EventStatus.NEW);
        assertThat(statusOf(w3HighOld)).isEqualTo(EventStatus.NEW);
        assertThat(statusOf(w3HighNew)).isEqualTo(EventStatus.NEW);
        assertThat(statusOf(w4Old)).isEqualTo(EventStatus.NEW);
        assertThat(statusOf(w4Investigating)).isEqualTo(EventStatus.INVESTIGATING);
    }

    @Test
    void secondRunIsIdempotent() {
        Instant now = Instant.now();
        saveEvent("0xw1", EventType.RAPID_TRANSFER, RiskLevel.MEDIUM,
                now.minus(Duration.ofHours(2)), EventStatus.NEW);
        saveEvent("0xw1", EventType.RAPID_TRANSFER, RiskLevel.MEDIUM,
                now.minus(Duration.ofHours(1)), EventStatus.NEW);

        assertThat(triageJob.triageNow(now)).isEqualTo(1);
        assertThat(triageJob.triageNow(now)).isEqualTo(0);
    }

    private EventStatus statusOf(DetectionEvent event) {
        return detectionEventRepository.findById(event.getId()).orElseThrow().getStatus();
    }

    private DetectionEvent saveEvent(
            String walletAddress,
            EventType eventType,
            RiskLevel riskLevel,
            Instant detectedAt,
            EventStatus status
    ) {
        DetectionEvent event = new DetectionEvent(
                eventType,
                riskLevel,
                70,
                "triage test event",
                walletAddress,
                detectedAt,
                null
        );
        if (status != null) {
            event.changeStatus(status);
        }
        return detectionEventRepository.save(event);
    }
}
