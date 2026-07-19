package com.chainwatch.backend.event.service;

import com.chainwatch.backend.event.config.EventExpiryProperties;
import com.chainwatch.backend.event.repository.DetectionEventRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저위험(MEDIUM/LOW) 미처리 backlog 자동 만료 잡.
 *
 * <p>RAPID_TRANSFER/FAN_OUT 같은 패턴 룰은 메인넷 실데이터에서 다량 발화할 수 있고,
 * 저위험 이벤트는 분석가가 전수 처리하지 않으므로 NEW 상태로 무한 누적된다.
 * 보존 기간이 지난 저위험 NEW 이벤트를 삭제 대신 RESOLVED + 사유로 일괄 종결해
 * 통계·감사 추적은 남기고 대응 큐만 정리한다. CRITICAL/HIGH는 절대 건드리지 않는다.
 */
@Component
public class LowRiskBacklogExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(LowRiskBacklogExpiryJob.class);

    static final String RESOLUTION_REASON_PREFIX = "자동 만료: 저위험 미처리 이벤트 보존 기간";

    private final EventExpiryProperties properties;
    private final DetectionEventRepository detectionEventRepository;

    public LowRiskBacklogExpiryJob(
            EventExpiryProperties properties,
            DetectionEventRepository detectionEventRepository
    ) {
        this.properties = properties;
        this.detectionEventRepository = detectionEventRepository;
    }

    @Scheduled(fixedDelayString = "${chainwatch.events.expiry.interval-ms:3600000}")
    @Transactional
    public void expire() {
        if (!properties.enabled()) {
            return;
        }
        int expired = expireNow(Instant.now());
        if (expired > 0) {
            log.info("[EVENT_EXPIRY] auto-resolved {} low-risk backlog event(s) older than {} day(s)",
                    expired, properties.maxAgeDays());
        }
    }

    /** 스케줄과 분리된 실행 본체 (테스트에서 기준 시각을 주입해 호출). */
    @Transactional
    public int expireNow(Instant now) {
        Instant cutoff = now.minus(Duration.ofDays(properties.maxAgeDays()));
        String reason = RESOLUTION_REASON_PREFIX + "(" + properties.maxAgeDays() + "일) 초과";
        return detectionEventRepository.expireLowRiskBacklog(cutoff, reason, now);
    }
}
