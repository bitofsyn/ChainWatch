package com.chainwatch.backend.agentops.service;

import com.chainwatch.backend.agentops.api.AgentOpsSnapshotResponse;
import com.chainwatch.backend.agentops.api.AgentOpsSnapshotResponse.Alert;
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
import com.chainwatch.backend.collector.config.CollectorProperties;
import com.chainwatch.backend.collector.service.BlockCollectionService;
import com.chainwatch.backend.common.exception.ResourceNotFoundException;
import com.chainwatch.backend.event.domain.DetectionEvent;
import com.chainwatch.backend.event.domain.EventStatus;
import com.chainwatch.backend.event.domain.RiskLevel;
import com.chainwatch.backend.event.repository.DetectionEventRepository;
import com.chainwatch.backend.notification.config.NotificationProperties;
import com.chainwatch.backend.transaction.repository.TransactionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 파이프라인 단계를 AI Agent 팀으로 투영해 팀 단위 운영 지표를 제공한다.
 * 큐/처리량/실패는 실제 저장 데이터(이벤트, AI 리포트, 트랜잭션)에서 도출하고,
 * 측정 수단이 없는 지표(평균 처리 시간 등)는 0으로 내려 프론트에서 "-"로 표기한다.
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

    public AgentOpsService(
            DetectionEventRepository detectionEventRepository,
            AiAnalysisReportRepository aiAnalysisReportRepository,
            TransactionRepository transactionRepository,
            BlockCollectionService blockCollectionService,
            CollectorProperties collectorProperties,
            AiAnalysisProperties aiAnalysisProperties,
            NotificationProperties notificationProperties
    ) {
        this.detectionEventRepository = detectionEventRepository;
        this.aiAnalysisReportRepository = aiAnalysisReportRepository;
        this.transactionRepository = transactionRepository;
        this.blockCollectionService = blockCollectionService;
        this.collectorProperties = collectorProperties;
        this.aiAnalysisProperties = aiAnalysisProperties;
        this.notificationProperties = notificationProperties;
    }

    @Transactional(readOnly = true)
    public AgentOpsSnapshotResponse snapshot() {
        Instant now = Instant.now();
        List<Team> teams = buildTeams(now);
        List<Handoff> handoffs = buildHandoffs();
        return new AgentOpsSnapshotResponse(buildOverview(now, teams), teams, handoffs);
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
        long lastBlock = blockCollectionService.lastCollectedBlockNumber();
        boolean collectorEnabled = collectorProperties.enabled();

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
                collectorEnabled ? "healthy" : "degraded",
                collectorEnabled ? null : "자동 수집 비활성 — 수동 트리거로만 동작 (chainwatch.collector.enabled=false)",
                new QueueMetric(0, 0, 0, 0, 0),
                100.0,
                0,
                transactions1h,
                "analysis",
                List.of("block.stream", "tx.batch"),
                List.of("detection.event"),
                List.of(
                        new SubAgent("rule-screener", "rule-screener", "Rule Engine 판정",
                                collectorEnabled ? "working" : "idle",
                                collectorEnabled ? "실시간 트랜잭션 스크리닝" : null),
                        new SubAgent("collector", "collector", "블록 수집",
                                collectorEnabled ? "working" : "idle",
                                lastBlock >= 0 ? "마지막 수집 블록 " + lastBlock : null)
                ),
                recentTasks,
                List.of(),
                List.of(
                        new SlaTarget("블록 수집 연속성", "수집 이력 존재",
                                lastBlock >= 0 ? "마지막 블록 " + lastBlock : "수집 이력 없음", lastBlock >= 0),
                        new SlaTarget("시간당 수집 트랜잭션", "> 0건", transactions1h + "건", transactions1h > 0),
                        new SlaTarget("시간당 탐지 이벤트", "집계", events1h + "건", true)
                ),
                now
        );
    }

    private Team analysisTeam(Instant now, Instant hourAgo) {
        long backlog = detectionEventRepository.countPendingAnalysis(ANALYSIS_TARGET_LEVELS);
        long completed1h = aiAnalysisReportRepository.countByStatusAndAnalyzedAtAfter(AnalysisStatus.COMPLETED, hourAgo);
        long failed1h = aiAnalysisReportRepository.countByStatusAndAnalyzedAtAfter(AnalysisStatus.FAILED, hourAgo);
        long totalFailed = aiAnalysisReportRepository.countByStatus(AnalysisStatus.FAILED);
        Instant oldestPending = detectionEventRepository.oldestPendingAnalysisDetectedAt(ANALYSIS_TARGET_LEVELS);
        long oldestWaitSeconds = oldestPending != null ? Math.max(0, Duration.between(oldestPending, now).toSeconds()) : 0;
        boolean aiEnabled = aiAnalysisProperties.enabled();

        double successRate = completed1h + failed1h > 0
                ? Math.round(completed1h * 1000.0 / (completed1h + failed1h)) / 10.0
                : 100.0;

        String status;
        String statusReason;
        if (!aiEnabled) {
            status = "blocked";
            statusReason = "AI 분석 비활성 (chainwatch.ai.enabled=false) — 고위험 이벤트 해설이 생성되지 않음";
        } else if (backlog >= ANALYSIS_BACKLOG_WARN || failed1h > completed1h && failed1h > 0) {
            status = "degraded";
            statusReason = backlog >= ANALYSIS_BACKLOG_WARN
                    ? "미분석 고위험 이벤트 " + backlog + "건 적체"
                    : "최근 1시간 분석 실패(" + failed1h + "건)가 성공(" + completed1h + "건)보다 많음";
        } else {
            status = "healthy";
            statusReason = null;
        }

        List<TaskRecord> recentTasks = aiAnalysisReportRepository.findTop6ByOrderByAnalyzedAtDesc().stream()
                .map(this::toAnalysisTask)
                .toList();
        List<TaskRecord> recentFailures = aiAnalysisReportRepository
                .findTop4ByStatusOrderByAnalyzedAtDesc(AnalysisStatus.FAILED).stream()
                .map(this::toAnalysisTask)
                .toList();

        return new Team(
                "analysis",
                "Analysis Team",
                "탐지 이벤트 AI 심층 분석·리포트 생성",
                "Detection Team이 생성한 고위험 이벤트에 대해 " + aiAnalysisProperties.provider()
                        + " 기반 리스크 해설 리포트를 생성하고 Triage Team으로 핸드오프합니다.",
                status,
                statusReason,
                new QueueMetric(backlog, 0, 0, failed1h, oldestWaitSeconds),
                successRate,
                0,
                completed1h,
                "triage",
                List.of("detection.event"),
                List.of("analysis.report"),
                List.of(
                        new SubAgent("llm-analyst", "llm-analyst",
                                aiAnalysisProperties.provider() + " 리스크 해설 (" + aiAnalysisProperties.model() + ")",
                                !aiEnabled ? "error" : backlog > 0 ? "working" : "idle",
                                backlog > 0 ? "미분석 고위험 이벤트 " + backlog + "건 대기" : null),
                        new SubAgent("report-writer", "report-writer", "리포트 저장·이벤트 연결",
                                "idle", null)
                ),
                recentTasks,
                recentFailures,
                List.of(
                        new SlaTarget("미분석 백로그", "< " + ANALYSIS_BACKLOG_WARN + "건", backlog + "건",
                                backlog < ANALYSIS_BACKLOG_WARN),
                        new SlaTarget("1시간 성공률", "≥ 97%", successRate + "%", successRate >= 97),
                        new SlaTarget("누적 분석 실패", "집계", totalFailed + "건", true)
                ),
                now
        );
    }

    private Team triageTeam(Instant now, Map<EventStatus, Long> statusCounts) {
        long newCount = statusCounts.getOrDefault(EventStatus.NEW, 0L);
        long inProgress = statusCounts.getOrDefault(EventStatus.ACKNOWLEDGED, 0L)
                + statusCounts.getOrDefault(EventStatus.INVESTIGATING, 0L);
        long resolved = statusCounts.getOrDefault(EventStatus.RESOLVED, 0L);
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
        return new Team(
                "triage",
                "Triage Team",
                "탐지 이벤트 등급 확정·대응 상태 관리",
                "탐지/분석 결과를 검토해 이벤트 lifecycle(신규 → 접수 → 조사중 → 해결)을 전이시키고, 경보 대상을 Notification Team으로 라우팅합니다.",
                backlogged ? "degraded" : "healthy",
                backlogged ? "미처리(NEW) 이벤트 " + newCount + "건 적체 — 접수 처리 필요" : null,
                new QueueMetric(newCount, inProgress, 0, 0, oldestWaitSeconds),
                100.0,
                0,
                resolved,
                "notification",
                List.of("analysis.report", "detection.event"),
                List.of("alert.request", "event.status"),
                List.of(
                        new SubAgent("risk-grader", "risk-grader", "등급·우선순위 확정",
                                newCount > 0 ? "working" : "idle",
                                newCount > 0 ? "미처리 이벤트 " + newCount + "건 검토 대기" : null),
                        new SubAgent("status-tracker", "status-tracker", "lifecycle 상태 전이",
                                inProgress > 0 ? "working" : "idle",
                                inProgress > 0 ? "조사 진행 중 " + inProgress + "건" : null)
                ),
                recentTasks,
                List.of(),
                List.of(
                        new SlaTarget("미처리(NEW) 이벤트", "< " + TRIAGE_BACKLOG_WARN + "건", newCount + "건", !backlogged),
                        new SlaTarget("해결 누적", "집계", resolved + "건", true)
                ),
                now
        );
    }

    private Team notificationTeam(Instant now) {
        boolean enabled = notificationProperties.enabled();
        boolean slackConfigured = hasText(notificationProperties.slackWebhookUrl());
        boolean discordConfigured = hasText(notificationProperties.discordWebhookUrl());
        boolean channelConfigured = slackConfigured || discordConfigured;

        String status;
        String statusReason;
        if (enabled && channelConfigured) {
            status = "healthy";
            statusReason = null;
        } else if (enabled) {
            status = "blocked";
            statusReason = "웹훅 채널 미설정 — 경보를 발송할 수 없음 (Slack/Discord webhook URL 필요)";
        } else {
            status = "degraded";
            statusReason = "알림 비활성 (chainwatch.notification.enabled=false)";
        }

        return new Team(
                "notification",
                "Notification Team",
                "채널별 경보 발송(Slack·Discord 웹훅)",
                "위험 점수 " + notificationProperties.minRiskScore() + "점 이상 이벤트를 웹훅 채널로 발송하고, "
                        + notificationProperties.dedupTtlMinutes() + "분 동안 중복 경보를 억제합니다.",
                status,
                statusReason,
                new QueueMetric(0, 0, 0, 0, 0),
                100.0,
                0,
                0,
                "ops",
                List.of("alert.request"),
                List.of("alert.delivery"),
                List.of(
                        new SubAgent("slack-sender", "slack-sender", "Slack 웹훅 발송",
                                slackConfigured && enabled ? "working" : "idle",
                                slackConfigured ? null : "webhook URL 미설정"),
                        new SubAgent("discord-sender", "discord-sender", "Discord 웹훅 발송",
                                discordConfigured && enabled ? "working" : "idle",
                                discordConfigured ? null : "webhook URL 미설정"),
                        new SubAgent("deduplicator", "deduplicator", "중복 경보 억제",
                                enabled ? "working" : "idle",
                                "억제 TTL " + notificationProperties.dedupTtlMinutes() + "분")
                ),
                List.of(),
                List.of(),
                List.of(
                        new SlaTarget("발송 채널 구성", "1개 이상",
                                (slackConfigured ? "Slack 연결됨" : "Slack 미설정") + " · "
                                        + (discordConfigured ? "Discord 연결됨" : "Discord 미설정"),
                                channelConfigured),
                        new SlaTarget("알림 활성화", "enabled", enabled ? "활성" : "비활성", enabled)
                ),
                now
        );
    }

    private Team opsTeam(Instant now) {
        long lastBlock = blockCollectionService.lastCollectedBlockNumber();
        return new Team(
                "ops",
                "Ops Team",
                "파이프라인 감시·장애 대응·에스컬레이션 처리",
                "수집 → Kafka → 탐지 → 분석 → 알림 파이프라인 전체를 감시합니다. 구성 요소별 실시간 점검은 관리자 > 파이프라인 상태에서 수행합니다.",
                "healthy",
                null,
                new QueueMetric(0, 0, 0, 0, 0),
                100.0,
                0,
                0,
                null,
                List.of("ops.escalation", "pipeline.health"),
                List.of("ops.action", "operator.page"),
                List.of(
                        new SubAgent("health-watcher", "health-watcher", "구성 요소 헬스 체크",
                                "working", "파이프라인 상태 탭에서 on-demand 점검"),
                        new SubAgent("dlt-monitor", "dlt-monitor", "Kafka DLT 감시",
                                "working", "raw-transactions DLT 유입 모니터링")
                ),
                List.of(),
                List.of(),
                List.of(
                        new SlaTarget("수집 파이프라인", "블록 이력 존재",
                                lastBlock >= 0 ? "마지막 블록 " + lastBlock : "수집 이력 없음", lastBlock >= 0)
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
                        team.name() + " 최근 1시간 실패 " + team.queue().failedLastHour() + "건", team.id(), now));
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
        };
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
