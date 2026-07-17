package com.chainwatch.backend.analysis.service;

import com.chainwatch.backend.analysis.config.AiAnalysisProperties;
import com.chainwatch.backend.analysis.domain.AiAnalysisReport;
import com.chainwatch.backend.analysis.domain.AnalysisStatus;
import com.chainwatch.backend.analysis.repository.AiAnalysisReportRepository;
import com.chainwatch.backend.event.domain.DetectionEvent;
import com.chainwatch.backend.event.domain.RiskLevel;
import com.chainwatch.backend.event.repository.DetectionEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 미분석 고위험(HIGH/CRITICAL) 이벤트를 오래된 순으로 자동 분석해 백로그를 소비한다.
 *
 * <p>스케줄 틱은 배치 선정과 PENDING 마킹만 수행하고, 실제 분석은 AI 전용 executor를 쓰는
 * {@link AsyncAiAnalysisRunner}에 위임한다. 단일 스레드 스케줄러를 점유하지 않으므로 블록 폴링
 * 수집과 간섭하지 않는다. PENDING 마킹이 선행되므로 다음 틱이 같은 이벤트를 중복 제출하지 않는다.
 *
 * <p>실패(FAILED)는 자동 재시도하지 않는다 — poison 이벤트의 무한 재시도로 프로바이더 쿼터를
 * 소진하는 것을 막기 위함이며, 재분석은 이벤트 상세에서 수동 트리거한다. 프로세스 중단 등으로
 * PENDING에 머문 리포트만 stale-pending-minutes 경과 후 재제출한다.
 */
@Component
public class AiAnalysisBacklogWorker {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisBacklogWorker.class);

    /** AgentOpsService의 분석 대상 기준과 동일하게 유지한다. */
    private static final List<RiskLevel> TARGET_LEVELS = List.of(RiskLevel.CRITICAL, RiskLevel.HIGH);

    private final AiAnalysisProperties properties;
    private final DetectionEventRepository detectionEventRepository;
    private final AiAnalysisReportRepository aiAnalysisReportRepository;
    private final AiAnalysisService aiAnalysisService;
    private final AsyncAiAnalysisRunner asyncAiAnalysisRunner;

    public AiAnalysisBacklogWorker(
            AiAnalysisProperties properties,
            DetectionEventRepository detectionEventRepository,
            AiAnalysisReportRepository aiAnalysisReportRepository,
            AiAnalysisService aiAnalysisService,
            AsyncAiAnalysisRunner asyncAiAnalysisRunner
    ) {
        this.properties = properties;
        this.detectionEventRepository = detectionEventRepository;
        this.aiAnalysisReportRepository = aiAnalysisReportRepository;
        this.aiAnalysisService = aiAnalysisService;
        this.asyncAiAnalysisRunner = asyncAiAnalysisRunner;
    }

    @Scheduled(fixedDelayString = "${chainwatch.ai.worker.poll-interval-ms:30000}")
    public void drain() {
        AiAnalysisProperties.Worker worker = properties.worker();
        if (!properties.enabled() || worker == null || !worker.enabled()) {
            return;
        }
        int submitted = submitPendingBacklog(worker.batchSize());
        submitted += resubmitStalePending(worker.stalePendingMinutes());
        if (submitted > 0) {
            log.info("[AI_WORKER] submitted {} event(s) for analysis (remaining backlog={})",
                    submitted, detectionEventRepository.countPendingAnalysis(TARGET_LEVELS));
        }
    }

    private int submitPendingBacklog(int batchSize) {
        List<DetectionEvent> batch = detectionEventRepository
                .findPendingAnalysisOldestFirst(TARGET_LEVELS, PageRequest.of(0, batchSize));
        for (DetectionEvent event : batch) {
            submit(event.getId());
        }
        return batch.size();
    }

    private int resubmitStalePending(long stalePendingMinutes) {
        Instant threshold = Instant.now().minus(Duration.ofMinutes(stalePendingMinutes));
        List<AiAnalysisReport> stale = aiAnalysisReportRepository
                .findTop3ByStatusAndAnalyzedAtBeforeOrderByAnalyzedAtAsc(AnalysisStatus.PENDING, threshold);
        for (AiAnalysisReport report : stale) {
            submit(report.getDetectionEvent().getId());
        }
        return stale.size();
    }

    /** PENDING 마킹(analyzedAt 갱신 → 중복/즉시 재선정 방지) 후 비동기 분석에 넘긴다. */
    private void submit(Long eventId) {
        aiAnalysisService.markPending(eventId);
        asyncAiAnalysisRunner.run(eventId);
    }
}
