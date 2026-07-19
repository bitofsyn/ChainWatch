package com.chainwatch.backend.agentops.service;

import com.chainwatch.backend.agentops.api.AgentOpsSnapshotResponse;
import com.chainwatch.backend.agentops.api.AgentOpsSnapshotResponse.Alert;
import com.chainwatch.backend.agentops.domain.AgentTaskFailure;
import com.chainwatch.backend.agentops.repository.AgentTaskFailureRepository;
import com.chainwatch.backend.agentops.api.AgentOpsSnapshotResponse.Handoff;
import com.chainwatch.backend.agentops.api.AgentOpsSnapshotResponse.Overview;
import com.chainwatch.backend.agentops.api.AgentOpsSnapshotResponse.QueueMetric;
import com.chainwatch.backend.agentops.api.AgentOpsSnapshotResponse.SlaTarget;
import com.chainwatch.backend.agentops.api.AgentOpsSnapshotResponse.SubAgent;
import com.chainwatch.backend.agentops.api.AgentOpsSnapshotResponse.TaskRecord;
import com.chainwatch.backend.agentops.api.AgentOpsSnapshotResponse.Team;
import com.chainwatch.backend.analysis.config.AiAnalysisProperties;
import com.chainwatch.backend.analysis.domain.AiAnalysisReport;
import com.chainwatch.backend.analysis.domain.AnalysisStatus;
import com.chainwatch.backend.analysis.repository.AiAnalysisReportRepository;
import com.chainwatch.backend.audit.repository.AuditLogRepository;
import com.chainwatch.backend.collector.config.CollectorProperties;
import com.chainwatch.backend.collector.service.BlockCollectionService;
import com.chainwatch.backend.common.exception.ResourceNotFoundException;
import com.chainwatch.backend.detection.config.DetectionProperties;
import com.chainwatch.backend.detection.consumer.RawTransactionDetectionConsumer;
import com.chainwatch.backend.event.domain.DetectionEvent;
import com.chainwatch.backend.event.domain.EventStatus;
import com.chainwatch.backend.event.domain.RiskLevel;
import com.chainwatch.backend.event.repository.DetectionEventRepository;
import com.chainwatch.backend.messaging.config.KafkaTopicProperties;
import com.chainwatch.backend.messaging.service.KafkaConsumerLagProbe;
import com.chainwatch.backend.notification.config.NotificationProperties;
import com.chainwatch.backend.notification.consumer.DetectedEventNotificationConsumer;
import com.chainwatch.backend.notification.domain.NotificationHistory;
import com.chainwatch.backend.notification.repository.NotificationHistoryRepository;
import com.chainwatch.backend.transaction.repository.TransactionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 파이프라인 단계를 AI Agent 팀으로 투영해 팀 단위 운영 지표를 제공한다.
 * 큐(컨슈머 랙)/처리량/실패/성공률/평균 처리 시간 모두 실측 데이터(이벤트, AI 리포트,
 * 트랜잭션, 발송 이력, 감사 로그, Kafka 오프셋, 처리 시간 트래커)에서 도출한다.
 * 측정 자체가 불가능한 지표만 0으로 내려 프론트에서 "-"로 표기한다.
 */
@Service
public class AgentOpsService {

    private static final List<RiskLevel> ANALYSIS_TARGET_LEVELS = List.of(RiskLevel.CRITICAL, RiskLevel.HIGH);
    private static final int ANALYSIS_BACKLOG_WARN = 10;
    private static final int TRIAGE_BACKLOG_WARN = 20;
    private static final int SUMMARY_MAX_LENGTH = 120;

    private final DetectionEventRepository detectionEventRepository;
    private final AiAnalysisReportRepository aiAnalysisReportRepository;
    private final TransactionRepository transactionRepository;
    private final BlockCollectionService blockCollectionService;
    private final CollectorProperties collectorProperties;
    private final AiAnalysisProperties aiAnalysisProperties;
    private final NotificationProperties notificationProperties;
    private final AgentTaskFailureRepository agentTaskFailureRepository;
    private final NotificationHistoryRepository notificationHistoryRepository;
    private final AuditLogRepository auditLogRepository;
    private final AgentFaultInjector faultInjector;
    private final AgentFaultService agentFaultService;
    private final AgentProcessingTracker processingTracker;
    private final DetectionProperties detectionProperties;
    private final KafkaTopicProperties kafkaTopicProperties;
    /** Kafka 미기동 환경에서는 빈 자체가 없으므로 ObjectProvider로 선택 주입한다. */
    private final ObjectProvider<KafkaConsumerLagProbe> lagProbeProvider;

    public AgentOpsService(
            DetectionEventRepository detectionEventRepository,
            AiAnalysisReportRepository aiAnalysisReportRepository,
            TransactionRepository transactionRepository,
            BlockCollectionService blockCollectionService,
            CollectorProperties collectorProperties,
            AiAnalysisProperties aiAnalysisProperties,
            NotificationProperties notificationProperties,
            AgentTaskFailureRepository agentTaskFailureRepository,
            NotificationHistoryRepository notificationHistoryRepository,
            AuditLogRepository auditLogRepository,
            AgentFaultInjector faultInjector,
            AgentFaultService agentFaultService,
            AgentProcessingTracker processingTracker,
            DetectionProperties detectionProperties,
            KafkaTopicProperties kafkaTopicProperties,
            ObjectProvider<KafkaConsumerLagProbe> lagProbeProvider
    ) {
        this.detectionEventRepository = detectionEventRepository;
        this.aiAnalysisReportRepository = aiAnalysisReportRepository;
        this.transactionRepository = transactionRepository;
        this.blockCollectionService = blockCollectionService;
        this.collectorProperties = collectorProperties;
        this.aiAnalysisProperties = aiAnalysisProperties;
        this.notificationProperties = notificationProperties;
        this.agentTaskFailureRepository = agentTaskFailureRepository;
        this.notificationHistoryRepository = notificationHistoryRepository;
        this.auditLogRepository = auditLogRepository;
        this.faultInjector = faultInjector;
        this.agentFaultService = agentFaultService;
        this.processingTracker = processingTracker;
        this.detectionProperties = detectionProperties;
        this.kafkaTopicProperties = kafkaTopicProperties;
        this.lagProbeProvider = lagProbeProvider;
    }

    @Transactional(readOnly = true)
    public AgentOpsSnapshotResponse snapshot() {
        Instant now = Instant.now();
        List<Team> teams = buildTeams(now);
        List<Handoff> handoffs = buildHandoffs();
        return new AgentOpsSnapshotResponse(
                buildOverview(now, teams), teams, handoffs, agentFaultService.statuses());
    }

    @Transactional(readOnly = true)
    public Team team(String teamId) {
        return buildTeams(Instant.now()).stream()
                .filter(team -> team.id().equals(teamId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("agent team not found: " + teamId));
    }

    @Transactional(readOnly = true)
    public List<Handoff> handoffs(int limit) {
        List<Handoff> handoffs = buildHandoffs();
        return handoffs.subList(0, Math.min(limit, handoffs.size()));
    }

    private List<Team> buildTeams(Instant now) {
        Instant hourAgo = now.minus(Duration.ofHours(1));
        Map<EventStatus, Long> statusCounts = statusCounts();
        return List.of(
                detectionTeam(now, hourAgo),
                analysisTeam(now, hourAgo),
                triageTeam(now, statusCounts),
                notificationTeam(now),
                opsTeam(now)
        );
    }

    private Team detectionTeam(Instant now, Instant hourAgo) {
        long transactions1h = transactionRepository.countByTimestampAfter(hourAgo);
        long events1h = detectionEventRepository.countByDetectedAtAfter(hourAgo);
        long failed1h = agentTaskFailureRepository.countByTeamIdAndOccurredAtAfter("detection", hourAgo);
        long lastBlock = blockCollectionService.lastCollectedBlockNumber();
        boolean collectorEnabled = collectorProperties.enabled();
        boolean faultActive = faultInjector.isActive("detection");
        double successRate = successRate(transactions1h, failed1h);
        // 대기 큐 = raw-transactions 컨슈머 랙(미소비 메시지). SYNC 모드는 큐 자체가 없다.
        long queued = detectionProperties.isKafkaMode()
                ? consumerLag(RawTransactionDetectionConsumer.GROUP_ID, kafkaTopicProperties.rawTransactions())
                : 0;
        long avgProcessingMs = processingTracker.averageMillis("detection");

        String status;
        String statusReason;
        if (faultActive) {
            status = "degraded";
            statusReason = "장애 주입 활성 — 트랜잭션 스크리닝이 강제 실패 처리되는 중";
        } else if (!collectorEnabled) {
            status = "degraded";
            statusReason = "자동 수집 비활성 — 수동 트리거로만 동작 (chainwatch.collector.enabled=false)";
        } else if (failed1h > 0) {
            status = "degraded";
            statusReason = "최근 1시간 스크리닝 실패 " + num(failed1h) + "건";
        } else {
            status = "healthy";
            statusReason = null;
        }

        List<TaskRecord> recentTasks = detectionEventRepository.findTop6ByOrderByDetectedAtDesc().stream()
                .map(event -> new TaskRecord(
                        "det-evt-" + event.getId(),
                        "이벤트 #" + event.getId() + " " + event.getEventType() + " 탐지",
                        "success",
                        event.getDetectedAt(),
                        null,
                        truncate(event.getSummary())
                ))
                .toList();

        return new Team(
                "detection",
                "Detection Team",
                "온체인 트랜잭션 실시간 스크리닝",
                "수집기가 넘긴 트랜잭션 스트림을 Rule Engine으로 판정하고, 임계치를 넘는 이벤트를 생성해 Analysis Team으로 핸드오프합니다.",
                status,
                statusReason,
                new QueueMetric(queued, 0, 0, failed1h, 0),
                successRate,
                avgProcessingMs,
                transactions1h,
                "analysis",
                List.of("block.stream", "tx.batch"),
                List.of("detection.event"),
                List.of(
                        new SubAgent("rule-screener", "rule-screener", "Rule Engine 판정",
                                faultActive ? "error" : collectorEnabled ? "working" : "idle",
                                faultActive ? "장애 주입 활성 — 스크리닝 강제 실패"
                                        : collectorEnabled ? "실시간 트랜잭션 스크리닝" : null),
                        new SubAgent("collector", "collector", "블록 수집",
                                collectorEnabled ? "working" : "idle",
                                lastBlock >= 0 ? "마지막 수집 블록 " + lastBlock : null)
                ),
                recentTasks,
                recentFailures("detection"),
                List.of(
                        new SlaTarget("블록 수집 연속성", "수집 이력 존재",
                                lastBlock >= 0 ? "마지막 블록 " + lastBlock : "수집 이력 없음", lastBlock >= 0),
                        new SlaTarget("시간당 수집 트랜잭션", "> 0건", num(transactions1h) + "건", transactions1h > 0),
                        new SlaTarget("시간당 탐지 이벤트", "집계", num(events1h) + "건", true),
                        new SlaTarget("1시간 성공률", "≥ 97%", successRate + "%", successRate >= 97)
                ),
                now
        );
    }

    private Team analysisTeam(Instant now, Instant hourAgo) {
        long backlog = detectionEventRepository.countPendingAnalysis(ANALYSIS_TARGET_LEVELS);
        long inProgress = aiAnalysisReportRepository.countByStatus(AnalysisStatus.PENDING);
        long completed1h = aiAnalysisReportRepository.countByStatusAndAnalyzedAtAfter(AnalysisStatus.COMPLETED, hourAgo);
        long reportFailed1h = aiAnalysisReportRepository.countByStatusAndAnalyzedAtAfter(AnalysisStatus.FAILED, hourAgo);
        long injectedFailed1h = agentTaskFailureRepository.countByTeamIdAndOccurredAtAfter("analysis", hourAgo);
        long failed1h = reportFailed1h + injectedFailed1h;
        long totalFailed = aiAnalysisReportRepository.countByStatus(AnalysisStatus.FAILED);
        Instant oldestPending = detectionEventRepository.oldestPendingAnalysisDetectedAt(ANALYSIS_TARGET_LEVELS);
        long oldestWaitSeconds = oldestPending != null ? Math.max(0, Duration.between(oldestPending, now).toSeconds()) : 0;
        boolean aiEnabled = aiAnalysisProperties.enabled();
        boolean faultActive = faultInjector.isActive("analysis");

        double successRate = successRate(completed1h, failed1h);
        // 재시도 = stale-pending 임계를 넘겨 워커가 재제출할 PENDING 리포트 수.
        long retrying = aiAnalysisProperties.worker() != null
                ? aiAnalysisReportRepository.countByStatusAndAnalyzedAtBefore(
                        AnalysisStatus.PENDING,
                        now.minus(Duration.ofMinutes(aiAnalysisProperties.worker().stalePendingMinutes())))
                : 0;
        long avgProcessingMs = roundAverage(
                aiAnalysisReportRepository.averageProcessingMs(AnalysisStatus.COMPLETED, hourAgo));

        String status;
        String statusReason;
        if (!aiEnabled) {
            status = "blocked";
            statusReason = "AI 분석 비활성 (chainwatch.ai.enabled=false) — 고위험 이벤트 해설이 생성되지 않음";
        } else if (faultActive) {
            status = "degraded";
            statusReason = "장애 주입 활성 — AI 분석 요청이 강제 실패 처리되는 중";
        } else if (backlog >= ANALYSIS_BACKLOG_WARN || failed1h > completed1h && failed1h > 0) {
            status = "degraded";
            statusReason = backlog >= ANALYSIS_BACKLOG_WARN
                    ? "미분석 고위험 이벤트 " + num(backlog) + "건 적체"
                    : "최근 1시간 분석 실패(" + num(failed1h) + "건)가 성공(" + num(completed1h) + "건)보다 많음";
        } else {
            status = "healthy";
            statusReason = null;
        }

        List<TaskRecord> recentTasks = aiAnalysisReportRepository.findTop6ByOrderByAnalyzedAtDesc().stream()
                .map(this::toAnalysisTask)
                .toList();
        List<TaskRecord> recentFailures = mergeRecentFailures(
                aiAnalysisReportRepository
                        .findTop4ByStatusOrderByAnalyzedAtDesc(AnalysisStatus.FAILED).stream()
                        .map(this::toAnalysisTask)
                        .toList(),
                recentFailures("analysis")
        );

        return new Team(
                "analysis",
                "Analysis Team",
                "탐지 이벤트 AI 심층 분석·리포트 생성",
                "Detection Team이 생성한 고위험 이벤트에 대해 " + aiAnalysisProperties.provider()
                        + " 기반 리스크 해설 리포트를 생성하고 Triage Team으로 핸드오프합니다.",
                status,
                statusReason,
                new QueueMetric(backlog, inProgress, retrying, failed1h, oldestWaitSeconds),
                successRate,
                avgProcessingMs,
                completed1h,
                "triage",
                List.of("detection.event"),
                List.of("analysis.report"),
                List.of(
                        new SubAgent("llm-analyst", "llm-analyst",
                                aiAnalysisProperties.provider() + " 리스크 해설 (" + aiAnalysisProperties.model() + ")",
                                !aiEnabled || faultActive ? "error" : backlog > 0 || inProgress > 0 ? "working" : "idle",
                                faultActive ? "장애 주입 활성 — 분석 강제 실패"
                                        : inProgress > 0
                                                ? "분석 진행 중 " + num(inProgress) + "건 · 대기 " + num(backlog) + "건"
                                                : backlog > 0 ? "미분석 고위험 이벤트 " + num(backlog) + "건 대기" : null),
                        new SubAgent("report-writer", "report-writer", "리포트 저장·이벤트 연결",
                                "idle", null)
                ),
                recentTasks,
                recentFailures,
                List.of(
                        new SlaTarget("미분석 백로그", "< " + ANALYSIS_BACKLOG_WARN + "건", num(backlog) + "건",
                                backlog < ANALYSIS_BACKLOG_WARN),
                        new SlaTarget("1시간 성공률", "≥ 97%", successRate + "%", successRate >= 97),
                        new SlaTarget("누적 분석 실패", "집계", num(totalFailed) + "건", true)
                ),
                now
        );
    }

    private Team triageTeam(Instant now, Map<EventStatus, Long> statusCounts) {
        Instant hourAgo = now.minus(Duration.ofHours(1));
        long newCount = statusCounts.getOrDefault(EventStatus.NEW, 0L);
        long inProgress = statusCounts.getOrDefault(EventStatus.ACKNOWLEDGED, 0L)
                + statusCounts.getOrDefault(EventStatus.INVESTIGATING, 0L);
        long resolved = statusCounts.getOrDefault(EventStatus.RESOLVED, 0L);
        long transitions1h = auditLogRepository.countByActionAndCreatedAtAfter("EVENT_STATUS_CHANGE", hourAgo);
        long failed1h = agentTaskFailureRepository.countByTeamIdAndOccurredAtAfter("triage", hourAgo);
        boolean faultActive = faultInjector.isActive("triage");
        double successRate = successRate(transitions1h, failed1h);
        Instant oldestUnresolved = detectionEventRepository.oldestUnresolvedDetectedAt();
        long oldestWaitSeconds = oldestUnresolved != null
                ? Math.max(0, Duration.between(oldestUnresolved, now).toSeconds())
                : 0;

        List<TaskRecord> recentTasks = detectionEventRepository
                .findTop6ByStatusInOrderByDetectedAtDesc(
                        List.of(EventStatus.ACKNOWLEDGED, EventStatus.INVESTIGATING, EventStatus.RESOLVED))
                .stream()
                .map(event -> new TaskRecord(
                        "tri-evt-" + event.getId(),
                        "이벤트 #" + event.getId() + " " + statusLabel(event.getStatus()),
                        event.getStatus() == EventStatus.RESOLVED ? "success" : "in_progress",
                        event.getDetectedAt(),
                        null,
                        truncate(event.getSummary())
                ))
                .toList();

        boolean backlogged = newCount >= TRIAGE_BACKLOG_WARN;

        String status;
        String statusReason;
        if (faultActive) {
            status = "degraded";
            statusReason = "장애 주입 활성 — 이벤트 상태 전이가 강제 실패 처리되는 중";
        } else if (backlogged) {
            status = "degraded";
            statusReason = "미처리(NEW) 이벤트 " + num(newCount) + "건 적체 — 접수 처리 필요";
        } else if (failed1h > 0) {
            status = "degraded";
            statusReason = "최근 1시간 상태 전이 실패 " + num(failed1h) + "건";
        } else {
            status = "healthy";
            statusReason = null;
        }

        return new Team(
                "triage",
                "Triage Team",
                "탐지 이벤트 등급 확정·대응 상태 관리",
                "탐지/분석 결과를 검토해 이벤트 lifecycle(신규 → 접수 → 조사중 → 해결)을 전이시키고, 경보 대상을 Notification Team으로 라우팅합니다.",
                status,
                statusReason,
                new QueueMetric(newCount, inProgress, 0, failed1h, oldestWaitSeconds),
                successRate,
                processingTracker.averageMillis("triage"),
                transitions1h,
                "notification",
                List.of("analysis.report", "detection.event"),
                List.of("alert.request", "event.status"),
                List.of(
                        new SubAgent("risk-grader", "risk-grader", "등급·우선순위 확정",
                                newCount > 0 ? "working" : "idle",
                                newCount > 0 ? "미처리 이벤트 " + num(newCount) + "건 검토 대기" : null),
                        new SubAgent("status-tracker", "status-tracker", "lifecycle 상태 전이",
                                faultActive ? "error" : inProgress > 0 ? "working" : "idle",
                                faultActive ? "장애 주입 활성 — 전이 강제 실패"
                                        : inProgress > 0 ? "조사 진행 중 " + num(inProgress) + "건" : null)
                ),
                recentTasks,
                recentFailures("triage"),
                List.of(
                        new SlaTarget("미처리(NEW) 이벤트", "< " + TRIAGE_BACKLOG_WARN + "건", num(newCount) + "건", !backlogged),
                        new SlaTarget("1시간 성공률", "≥ 97%", successRate + "%", successRate >= 97),
                        new SlaTarget("해결 누적", "집계", num(resolved) + "건", true)
                ),
                now
        );
    }

    private Team notificationTeam(Instant now) {
        Instant hourAgo = now.minus(Duration.ofHours(1));
        boolean enabled = notificationProperties.enabled();
        boolean slackConfigured = hasText(notificationProperties.slackWebhookUrl());
        boolean discordConfigured = hasText(notificationProperties.discordWebhookUrl());
        boolean channelConfigured = slackConfigured || discordConfigured;
        boolean faultActive = faultInjector.isActive("notification");
        long sent1h = notificationHistoryRepository.countBySuccessTrueAndSentAtAfter(hourAgo);
        long failed1h = notificationHistoryRepository.countBySuccessFalseAndSentAtAfter(hourAgo);
        double successRate = successRate(sent1h, failed1h);
        // 대기 큐 = detected-events 컨슈머 랙(아직 알림 판정을 거치지 않은 이벤트 수).
        long queued = consumerLag(
                DetectedEventNotificationConsumer.GROUP_ID, kafkaTopicProperties.detectedEvents());
        long avgProcessingMs = roundAverage(notificationHistoryRepository.averageDurationMs(hourAgo));

        String status;
        String statusReason;
        if (enabled && !channelConfigured) {
            status = "blocked";
            statusReason = "웹훅 채널 미설정 — 경보를 발송할 수 없음 (Slack/Discord webhook URL 필요)";
        } else if (faultActive) {
            status = "degraded";
            statusReason = "장애 주입 활성 — 경보 발송이 강제 실패 처리되는 중";
        } else if (!enabled) {
            status = "degraded";
            statusReason = "알림 비활성 (chainwatch.notification.enabled=false)";
        } else if (failed1h > 0) {
            status = "degraded";
            statusReason = "최근 1시간 발송 실패 " + num(failed1h) + "건";
        } else {
            status = "healthy";
            statusReason = null;
        }

        List<TaskRecord> recentTasks = notificationHistoryRepository.findTop6ByOrderBySentAtDesc().stream()
                .map(AgentOpsService::toNotificationTask)
                .toList();
        List<TaskRecord> recentFailures = notificationHistoryRepository
                .findTop4BySuccessFalseOrderBySentAtDesc().stream()
                .map(AgentOpsService::toNotificationTask)
                .toList();

        return new Team(
                "notification",
                "Notification Team",
                "채널별 경보 발송(Slack·Discord 웹훅)",
                "위험 점수 " + notificationProperties.minRiskScore() + "점 이상 이벤트를 웹훅 채널로 발송하고, "
                        + notificationProperties.dedupTtlMinutes() + "분 동안 중복 경보를 억제합니다.",
                status,
                statusReason,
                new QueueMetric(queued, 0, 0, failed1h, 0),
                successRate,
                avgProcessingMs,
                sent1h,
                "ops",
                List.of("alert.request"),
                List.of("alert.delivery"),
                List.of(
                        new SubAgent("slack-sender", "slack-sender", "Slack 웹훅 발송",
                                faultActive && slackConfigured ? "error"
                                        : slackConfigured && enabled ? "working" : "idle",
                                faultActive && slackConfigured ? "장애 주입 활성 — 발송 강제 실패"
                                        : slackConfigured ? null : "webhook URL 미설정"),
                        new SubAgent("discord-sender", "discord-sender", "Discord 웹훅 발송",
                                faultActive && discordConfigured ? "error"
                                        : discordConfigured && enabled ? "working" : "idle",
                                faultActive && discordConfigured ? "장애 주입 활성 — 발송 강제 실패"
                                        : discordConfigured ? null : "webhook URL 미설정"),
                        new SubAgent("deduplicator", "deduplicator", "중복 경보 억제",
                                enabled ? "working" : "idle",
                                "억제 TTL " + notificationProperties.dedupTtlMinutes() + "분")
                ),
                recentTasks,
                recentFailures,
                List.of(
                        new SlaTarget("발송 채널 구성", "1개 이상",
                                (slackConfigured ? "Slack 연결됨" : "Slack 미설정") + " · "
                                        + (discordConfigured ? "Discord 연결됨" : "Discord 미설정"),
                                channelConfigured),
                        new SlaTarget("알림 활성화", "enabled", enabled ? "활성" : "비활성", enabled),
                        new SlaTarget("1시간 성공률", "≥ 97%", successRate + "%", successRate >= 97)
                ),
                now
        );
    }

    private Team opsTeam(Instant now) {
        Instant hourAgo = now.minus(Duration.ofHours(1));
        long lastBlock = blockCollectionService.lastCollectedBlockNumber();
        long failed1h = agentTaskFailureRepository.countByTeamIdAndOccurredAtAfter("ops", hourAgo);
        boolean faultActive = faultInjector.isActive("ops");
        double successRate = successRate(0, failed1h);

        String status;
        String statusReason;
        if (faultActive) {
            status = "degraded";
            statusReason = "장애 주입 활성 — 에스컬레이션 처리가 강제 실패 처리되는 중";
        } else if (failed1h > 0) {
            status = "degraded";
            statusReason = "최근 1시간 에스컬레이션 처리 실패 " + num(failed1h) + "건";
        } else {
            status = "healthy";
            statusReason = null;
        }

        return new Team(
                "ops",
                "Ops Team",
                "파이프라인 감시·장애 대응·에스컬레이션 처리",
                "수집 → Kafka → 탐지 → 분석 → 알림 파이프라인 전체를 감시합니다. 구성 요소별 실시간 점검은 관리자 > 파이프라인 상태에서 수행합니다.",
                status,
                statusReason,
                new QueueMetric(0, 0, 0, failed1h, 0),
                successRate,
                0,
                0,
                null,
                List.of("ops.escalation", "pipeline.health"),
                List.of("ops.action", "operator.page"),
                List.of(
                        new SubAgent("health-watcher", "health-watcher", "구성 요소 헬스 체크",
                                faultActive ? "error" : "working",
                                faultActive ? "장애 주입 활성 — 에스컬레이션 강제 실패"
                                        : "파이프라인 상태 탭에서 on-demand 점검"),
                        new SubAgent("dlt-monitor", "dlt-monitor", "Kafka DLT 감시",
                                "working", "raw-transactions DLT 유입 모니터링")
                ),
                List.of(),
                recentFailures("ops"),
                List.of(
                        new SlaTarget("수집 파이프라인", "블록 이력 존재",
                                lastBlock >= 0 ? "마지막 블록 " + lastBlock : "수집 이력 없음", lastBlock >= 0),
                        new SlaTarget("1시간 에스컬레이션 실패", "0건", num(failed1h) + "건", failed1h == 0)
                ),
                now
        );
    }

    private Overview buildOverview(Instant now, List<Team> teams) {
        int activeTeams = (int) teams.stream().filter(team -> !"blocked".equals(team.status())).count();
        long throughput = teams.stream().mapToLong(Team::throughputPerHour).sum();
        long failed1h = teams.stream().mapToLong(team -> team.queue().failedLastHour()).sum();
        long retried1h = teams.stream().mapToLong(team -> team.queue().retrying()).sum();
        List<Long> measuredAvg = teams.stream().map(Team::avgProcessingMs).filter(avg -> avg > 0).toList();
        long avgResponseMs = measuredAvg.isEmpty()
                ? 0
                : Math.round(measuredAvg.stream().mapToLong(Long::longValue).average().orElse(0));
        return new Overview(
                now,
                activeTeams,
                teams.size(),
                throughput,
                avgResponseMs,
                failed1h,
                retried1h,
                pickBottleneck(teams),
                buildAlerts(now, teams)
        );
    }

    private String pickBottleneck(List<Team> teams) {
        return teams.stream()
                .max(Comparator
                        .comparingInt((Team team) -> statusWeight(team.status()))
                        .thenComparingLong(team -> team.queue().oldestWaitingSeconds())
                        .thenComparingLong(team -> team.queue().queued()))
                .filter(team -> !"healthy".equals(team.status()) || team.queue().queued() > 0)
                .map(Team::id)
                .orElse(null);
    }

    private List<Alert> buildAlerts(Instant now, List<Team> teams) {
        List<Alert> alerts = new ArrayList<>();
        for (Team team : teams) {
            if ("blocked".equals(team.status())) {
                alerts.add(new Alert("alert-" + team.id() + "-blocked", "critical",
                        team.name() + " 차단 — " + team.statusReason(), team.id(), now));
            } else if ("degraded".equals(team.status())) {
                alerts.add(new Alert("alert-" + team.id() + "-degraded", "warning",
                        team.name() + " 성능 저하 — " + team.statusReason(), team.id(), now));
            }
            if (team.queue().failedLastHour() > 0) {
                alerts.add(new Alert("alert-" + team.id() + "-failures", "warning",
                        team.name() + " 최근 1시간 실패 " + num(team.queue().failedLastHour()) + "건", team.id(), now));
            }
        }
        return alerts;
    }

    private List<Handoff> buildHandoffs() {
        List<Handoff> handoffs = new ArrayList<>();

        for (DetectionEvent event : detectionEventRepository.findTop6ByOrderByDetectedAtDesc()) {
            boolean analyzed = aiAnalysisReportRepository.findByDetectionEventId(event.getId()).isPresent();
            handoffs.add(new Handoff(
                    "ho-evt-" + event.getId(),
                    "detection",
                    "analysis",
                    "이벤트 #" + event.getId() + " " + event.getEventType(),
                    truncate(event.getSummary()),
                    analyzed ? "completed" : "queued",
                    event.getDetectedAt()
            ));
        }

        for (AiAnalysisReport report : aiAnalysisReportRepository.findTop6ByOrderByAnalyzedAtDesc()) {
            Long eventId = report.getDetectionEvent().getId();
            if (report.getStatus() == AnalysisStatus.COMPLETED) {
                handoffs.add(new Handoff(
                        "ho-rep-" + report.getId(),
                        "analysis",
                        "triage",
                        "이벤트 #" + eventId + " 분석 리포트",
                        "AI 분석 완료 (" + report.getProvider() + "/" + report.getModel() + ")",
                        "accepted",
                        report.getAnalyzedAt()
                ));
            } else {
                handoffs.add(new Handoff(
                        "ho-rep-" + report.getId(),
                        "analysis",
                        "ops",
                        "이벤트 #" + eventId + " 분석 실패 에스컬레이션",
                        truncate(report.getReport()),
                        "queued",
                        report.getAnalyzedAt()
                ));
            }
        }

        handoffs.sort(Comparator.comparing(Handoff::occurredAt).reversed());
        return handoffs;
    }

    private Map<EventStatus, Long> statusCounts() {
        Map<EventStatus, Long> counts = new EnumMap<>(EventStatus.class);
        for (Object[] row : detectionEventRepository.countGroupByStatus()) {
            EventStatus status = row[0] != null ? (EventStatus) row[0] : EventStatus.NEW;
            counts.merge(status, (Long) row[1], Long::sum);
        }
        return counts;
    }

    private TaskRecord toAnalysisTask(AiAnalysisReport report) {
        Long eventId = report.getDetectionEvent().getId();
        return new TaskRecord(
                "ana-rep-" + report.getId(),
                "이벤트 #" + eventId + " AI 분석",
                report.getStatus() == AnalysisStatus.COMPLETED ? "success" : "failed",
                report.getAnalyzedAt(),
                null,
                truncate(report.getStatus() == AnalysisStatus.COMPLETED ? report.getReport() : report.getPromptSummary())
        );
    }

    /** agent_task_failures 기반 최근 실패 4건을 실패 타임라인용 TaskRecord로 변환한다. */
    private List<TaskRecord> recentFailures(String teamId) {
        return agentTaskFailureRepository.findTop4ByTeamIdOrderByOccurredAtDesc(teamId).stream()
                .map(AgentOpsService::toFailureTask)
                .toList();
    }

    /** 서로 다른 실패 소스를 최신순으로 합쳐 4건까지만 노출한다. */
    private static List<TaskRecord> mergeRecentFailures(List<TaskRecord> first, List<TaskRecord> second) {
        List<TaskRecord> merged = new ArrayList<>(first);
        merged.addAll(second);
        merged.sort(Comparator.comparing(TaskRecord::startedAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return merged.subList(0, Math.min(4, merged.size()));
    }

    private static TaskRecord toFailureTask(AgentTaskFailure failure) {
        return new TaskRecord(
                "fail-" + failure.getTeamId() + "-" + failure.getId(),
                failure.getTitle(),
                "failed",
                failure.getOccurredAt(),
                null,
                failure.getDetail() != null ? failure.getDetail() : ""
        );
    }

    private static TaskRecord toNotificationTask(NotificationHistory history) {
        boolean success = Boolean.TRUE.equals(history.getSuccess());
        String subject = history.getEventId() != null
                ? "이벤트 #" + history.getEventId() + " " + history.getChannel() + " 경보 발송"
                : history.getChannel() + " 경보 발송 (드릴)";
        return new TaskRecord(
                "noti-" + history.getId(),
                subject,
                success ? "success" : "failed",
                history.getSentAt(),
                null,
                success
                        ? "발송 성공 · 위험 점수 " + history.getRiskScore()
                        : (history.getErrorMessage() != null ? history.getErrorMessage() : "발송 실패")
        );
    }

    /** 성공+실패 표본이 없으면 100%로 간주한다(측정 불가 상태를 실패로 오인하지 않도록). */
    private static double successRate(long success, long failed) {
        long total = success + failed;
        return total > 0 ? Math.round(success * 1000.0 / total) / 10.0 : 100.0;
    }

    /** Kafka 컨슈머 랙. 프로브가 없거나(브로커 미구성) 조회 실패 시 0으로 강등한다. */
    private long consumerLag(String groupId, String topic) {
        KafkaConsumerLagProbe probe = lagProbeProvider.getIfAvailable();
        if (probe == null || topic == null) {
            return 0;
        }
        OptionalLong lag = probe.consumerLag(groupId, topic);
        return lag.orElse(0);
    }

    /** avg 쿼리 결과(null 가능)를 ms 단위 long으로 반올림한다. 표본 없음 → 0("-" 표기). */
    private static long roundAverage(Double average) {
        return average != null ? Math.max(1, Math.round(average)) : 0;
    }

    private static int statusWeight(String status) {
        return switch (status) {
            case "blocked" -> 2;
            case "degraded" -> 1;
            default -> 0;
        };
    }

    private static String statusLabel(EventStatus status) {
        return switch (status) {
            case NEW -> "신규 접수 대기";
            case ACKNOWLEDGED -> "접수";
            case INVESTIGATING -> "조사 진행";
            case RESOLVED -> "해결 완료";
            case FALSE_POSITIVE -> "오탐 종결";
        };
    }

    /** 카운트 지표를 천 단위 구분 기호로 포맷한다(예: 1369514 → 1,369,514). 블록 번호·ID 등 식별자에는 쓰지 않는다. */
    private static String num(long value) {
        return String.format(Locale.KOREA, "%,d", value);
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= SUMMARY_MAX_LENGTH ? value : value.substring(0, SUMMARY_MAX_LENGTH) + "…";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
