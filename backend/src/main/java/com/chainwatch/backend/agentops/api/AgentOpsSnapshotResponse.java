package com.chainwatch.backend.agentops.api;

import java.time.Instant;
import java.util.List;

/**
 * AI Agent 팀 운영 콘솔 스냅샷. 프론트엔드 types.ts의 AgentOpsSnapshot 스키마와 1:1 대응한다.
 */
public record AgentOpsSnapshotResponse(
        Overview overview,
        List<Team> teams,
        List<Handoff> handoffs
) {

    public record Overview(
            Instant generatedAt,
            int activeTeams,
            int totalTeams,
            long throughputPerHour,
            long avgResponseMs,
            long failed1h,
            long retried1h,
            String bottleneckTeamId,
            List<Alert> alerts
    ) {
    }

    public record Alert(
            String id,
            String severity,
            String message,
            String teamId,
            Instant raisedAt
    ) {
    }

    public record Team(
            String id,
            String name,
            String role,
            String description,
            String status,
            String statusReason,
            QueueMetric queue,
            double successRate1h,
            long avgProcessingMs,
            long throughputPerHour,
            String lastHandoffTo,
            List<String> inputTypes,
            List<String> outputTypes,
            List<SubAgent> subAgents,
            List<TaskRecord> recentTasks,
            List<TaskRecord> recentFailures,
            List<SlaTarget> slaTargets,
            Instant updatedAt
    ) {
    }

    public record QueueMetric(
            long queued,
            long inProgress,
            long retrying,
            long failedLastHour,
            long oldestWaitingSeconds
    ) {
    }

    public record SubAgent(
            String id,
            String name,
            String role,
            String state,
            String currentTask
    ) {
    }

    public record TaskRecord(
            String id,
            String title,
            String outcome,
            Instant startedAt,
            Long durationMs,
            String detail
    ) {
    }

    public record SlaTarget(
            String metric,
            String target,
            String current,
            boolean met
    ) {
    }

    public record Handoff(
            String id,
            String fromTeamId,
            String toTeamId,
            String subject,
            String reason,
            String result,
            Instant occurredAt
    ) {
    }
}
