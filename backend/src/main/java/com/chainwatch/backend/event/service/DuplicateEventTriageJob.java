package com.chainwatch.backend.event.service;

import com.chainwatch.backend.audit.service.AuditLogService;
import com.chainwatch.backend.event.config.EventTriageProperties;
import com.chainwatch.backend.event.repository.DetectionEventRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저위험(MEDIUM/LOW) 중복 이벤트 자동 트리아지 잡.
 *
 * <p>패턴 룰(RAPID_TRANSFER/FAN_OUT 등)은 같은 지갑에서 반복 발화해 실질적으로 같은 사안이
 * 이벤트 여러 건으로 쌓인다. 만료 잡({@link LowRiskBacklogExpiryJob})은 보존 기간(기본 3일)을
 * 기다리지만, 이 잡은 같은 지갑·같은 유형의 더 최신 미처리 이벤트가 있으면 옛 중복 건을
 * 즉시 RESOLVED + 사유로 종결해 대응 큐를 지갑·유형당 최신 1건으로 유지한다.
 * CRITICAL/HIGH는 절대 건드리지 않는다.
 */
@Component
public class DuplicateEventTriageJob {

    private static final Logger log = LoggerFactory.getLogger(DuplicateEventTriageJob.class);

    static final String RESOLUTION_REASON = "자동 트리아지: 동일 지갑·유형의 더 최신 미처리 이벤트가 있어 중복 종결";
    static final String AUDIT_ACTION = "EVENT_AUTO_TRIAGE";
    private static final String SYSTEM_ACTOR = "system";

    private final EventTriageProperties properties;
    private final DetectionEventRepository detectionEventRepository;
    private final AuditLogService auditLogService;

    public DuplicateEventTriageJob(
            EventTriageProperties properties,
            DetectionEventRepository detectionEventRepository,
            AuditLogService auditLogService
    ) {
        this.properties = properties;
        this.detectionEventRepository = detectionEventRepository;
        this.auditLogService = auditLogService;
    }

    @Scheduled(fixedDelayString = "${chainwatch.events.triage.interval-ms:600000}")
    @Transactional
    public void triage() {
        if (!properties.enabled()) {
            return;
        }
        int triaged = triageNow(Instant.now());
        if (triaged > 0) {
            log.info("[EVENT_TRIAGE] auto-resolved {} duplicate low-risk event(s), keeping newest per wallet/type",
                    triaged);
        }
    }

    /** 스케줄과 분리된 실행 본체 (테스트에서 기준 시각을 주입해 호출). */
    @Transactional
    public int triageNow(Instant now) {
        int triaged = detectionEventRepository.triageDuplicateLowRiskBacklog(RESOLUTION_REASON, now);
        if (triaged > 0) {
            auditLogService.record(SYSTEM_ACTOR, null, AUDIT_ACTION, "DETECTION_EVENTS", "duplicates",
                    "저위험 중복 이벤트 " + triaged + "건 자동 종결 (지갑·유형당 최신 미처리 1건 유지)");
        }
        return triaged;
    }
}
