package com.chainwatch.backend.casework.service;

import com.chainwatch.backend.agentops.service.AgentFailureRecorder;
import com.chainwatch.backend.agentops.service.AgentFaultInjector;
import com.chainwatch.backend.agentops.service.AgentProcessingTracker;
import com.chainwatch.backend.audit.service.AuditLogService;
import com.chainwatch.backend.casework.domain.EventActivity;
import com.chainwatch.backend.casework.repository.EventActivityRepository;
import com.chainwatch.backend.common.exception.ConflictException;
import com.chainwatch.backend.common.exception.ResourceNotFoundException;
import com.chainwatch.backend.event.api.EventStatusUpdateRequest;
import com.chainwatch.backend.event.domain.DetectionEvent;
import com.chainwatch.backend.event.domain.EventStatus;
import com.chainwatch.backend.event.repository.DetectionEventRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 분석가의 이벤트 처리(상태 변경, 담당자 지정, 코멘트, 일괄 처리)를 수행하고
 * 그 내용을 이벤트 처리 이력으로 남긴다.
 */
@Service
public class EventWorkflowService {

    public static final int MAX_BULK_SIZE = 200;

    private static final String AUDIT_ACTION_STATUS_CHANGE = "EVENT_STATUS_CHANGE";
    private static final String AUDIT_ACTION_BULK_STATUS_CHANGE = "EVENT_BULK_STATUS_CHANGE";
    private static final String AUDIT_TARGET_TYPE_EVENT = "DETECTION_EVENT";

    private static final Map<EventStatus, String> STATUS_LABELS = Map.of(
            EventStatus.NEW, "신규",
            EventStatus.ACKNOWLEDGED, "접수",
            EventStatus.INVESTIGATING, "조사중",
            EventStatus.RESOLVED, "해결",
            EventStatus.FALSE_POSITIVE, "오탐 종결"
    );

    private final DetectionEventRepository detectionEventRepository;
    private final EventActivityRepository activityRepository;
    private final AuditLogService auditLogService;
    private final AgentFaultInjector faultInjector;
    private final AgentFailureRecorder failureRecorder;
    private final AgentProcessingTracker processingTracker;

    public EventWorkflowService(
            DetectionEventRepository detectionEventRepository,
            EventActivityRepository activityRepository,
            AuditLogService auditLogService,
            AgentFaultInjector faultInjector,
            AgentFailureRecorder failureRecorder,
            AgentProcessingTracker processingTracker
    ) {
        this.detectionEventRepository = detectionEventRepository;
        this.activityRepository = activityRepository;
        this.auditLogService = auditLogService;
        this.faultInjector = faultInjector;
        this.failureRecorder = failureRecorder;
        this.processingTracker = processingTracker;
    }

    /** 일괄 처리 결과. missingIds는 존재하지 않아 건너뛴 이벤트. */
    public record BulkResult(int updated, List<Long> missingIds) {
    }

    @Transactional
    public DetectionEvent changeStatus(Long id, EventStatusUpdateRequest request) {
        DetectionEvent event = detectionEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Detection event not found: " + id));
        rejectWhenFaultInjected(id, event.getStatus(), request.status());
        long startedNanos = System.nanoTime();
        EventStatus previousStatus = event.getStatus();
        apply(event, request);
        auditLogService.record(
                AUDIT_ACTION_STATUS_CHANGE,
                AUDIT_TARGET_TYPE_EVENT,
                String.valueOf(id),
                previousStatus + " -> " + request.status()
                        + (request.assignee() != null ? ", assignee=" + request.assignee() : "")
        );
        processingTracker.record("triage", System.nanoTime() - startedNanos);
        return event;
    }

    /** 여러 이벤트에 같은 상태 변경을 적용한다. 사유 검증 실패는 전체를 되돌린다(부분 적용 없음). */
    @Transactional
    public BulkResult changeStatusInBulk(List<Long> ids, EventStatusUpdateRequest request) {
        Set<Long> uniqueIds = new LinkedHashSet<>(ids);
        if (uniqueIds.isEmpty()) {
            throw new IllegalArgumentException("ids must not be empty");
        }
        if (uniqueIds.size() > MAX_BULK_SIZE) {
            throw new IllegalArgumentException("한 번에 처리할 수 있는 이벤트는 최대 " + MAX_BULK_SIZE + "건입니다.");
        }
        long startedNanos = System.nanoTime();
        List<DetectionEvent> events = detectionEventRepository.findAllById(uniqueIds);
        List<Long> missing = new ArrayList<>(uniqueIds);
        for (DetectionEvent event : events) {
            rejectWhenFaultInjected(event.getId(), event.getStatus(), request.status());
            apply(event, request);
            missing.remove(event.getId());
        }
        auditLogService.record(
                AUDIT_ACTION_BULK_STATUS_CHANGE,
                AUDIT_TARGET_TYPE_EVENT,
                events.size() + " events",
                "-> " + request.status()
                        + (request.assignee() != null ? ", assignee=" + request.assignee() : "")
                        + ", ids=" + events.stream().map(DetectionEvent::getId).toList()
        );
        processingTracker.record("triage", System.nanoTime() - startedNanos);
        return new BulkResult(events.size(), missing);
    }

    @Transactional
    public EventActivity addComment(Long eventId, String text) {
        if (!detectionEventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Detection event not found: " + eventId);
        }
        return activityRepository.save(
                new EventActivity(eventId, CurrentActor.name(), EventActivity.TYPE_COMMENT, text.strip()));
    }

    @Transactional(readOnly = true)
    public List<EventActivity> activities(Long eventId) {
        return activityRepository.findByEventIdOrderByCreatedAtDescIdDesc(eventId);
    }

    /** 도메인 규칙으로 상태를 바꾸고, 실제로 달라진 항목만 처리 이력에 남긴다. */
    private void apply(DetectionEvent event, EventStatusUpdateRequest request) {
        EventStatus previousStatus = event.getStatus();
        String previousAssignee = event.getAssignee();
        String previousNotes = event.getNotes();

        event.applyStatusChange(
                request.status(),
                request.assignee(),
                request.resolutionReason(),
                request.falsePositiveReason(),
                request.notes()
        );

        String actor = CurrentActor.name();
        if (previousStatus != event.getStatus()) {
            String detail = label(previousStatus) + " → " + label(event.getStatus());
            if (event.getStatus() == EventStatus.RESOLVED) {
                detail += " · 사유: " + event.getResolutionReason();
            } else if (event.getStatus() == EventStatus.FALSE_POSITIVE) {
                detail += " · 사유: " + event.getFalsePositiveReason();
            }
            activityRepository.save(new EventActivity(event.getId(), actor, EventActivity.TYPE_STATUS, detail));
        }
        if (!Objects.equals(previousAssignee, event.getAssignee())) {
            activityRepository.save(new EventActivity(event.getId(), actor, EventActivity.TYPE_ASSIGNMENT,
                    orUnassigned(previousAssignee) + " → " + orUnassigned(event.getAssignee())));
        }
        if (event.getNotes() != null && !Objects.equals(previousNotes, event.getNotes())) {
            activityRepository.save(
                    new EventActivity(event.getId(), actor, EventActivity.TYPE_COMMENT, event.getNotes()));
        }
    }

    private void rejectWhenFaultInjected(Long id, EventStatus from, EventStatus to) {
        // 장애 주입 활성 시 실패를 기록(REQUIRES_NEW라 롤백과 무관하게 남음)하고 전이를 거부한다.
        if (faultInjector.isActive("triage")) {
            failureRecorder.record("triage",
                    "이벤트 #" + id + " 상태 전이 실패",
                    "장애 주입 활성 — " + from + " → " + to + " 전이 거부", true);
            throw new ConflictException("장애 주입 활성 — Triage 팀 상태 전이가 강제 실패하도록 설정되어 있습니다.");
        }
    }

    private static String label(EventStatus status) {
        return STATUS_LABELS.getOrDefault(status, String.valueOf(status));
    }

    private static String orUnassigned(String assignee) {
        return assignee == null || assignee.isBlank() ? "미지정" : assignee;
    }
}
