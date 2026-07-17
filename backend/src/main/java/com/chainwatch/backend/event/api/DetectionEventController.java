package com.chainwatch.backend.event.api;

import com.chainwatch.backend.agentops.service.AgentFailureRecorder;
import com.chainwatch.backend.agentops.service.AgentFaultInjector;
import com.chainwatch.backend.agentops.service.AgentProcessingTracker;
import com.chainwatch.backend.analysis.api.AiAnalysisReportResponse;
import com.chainwatch.backend.analysis.service.AiAnalysisService;
import com.chainwatch.backend.audit.service.AuditLogService;
import com.chainwatch.backend.common.exception.ConflictException;
import com.chainwatch.backend.common.exception.ResourceNotFoundException;
import com.chainwatch.backend.event.repository.DetectionEventRepository;
import com.chainwatch.backend.event.domain.DetectionEvent;
import com.chainwatch.backend.event.domain.EventStatus;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class DetectionEventController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String AUDIT_ACTION_STATUS_CHANGE = "EVENT_STATUS_CHANGE";
    private static final String AUDIT_TARGET_TYPE_EVENT = "DETECTION_EVENT";

    private final DetectionEventRepository detectionEventRepository;
    private final AiAnalysisService aiAnalysisService;
    private final AuditLogService auditLogService;
    private final AgentFaultInjector faultInjector;
    private final AgentFailureRecorder failureRecorder;
    private final AgentProcessingTracker processingTracker;

    public DetectionEventController(
            DetectionEventRepository detectionEventRepository,
            AiAnalysisService aiAnalysisService,
            AuditLogService auditLogService,
            AgentFaultInjector faultInjector,
            AgentFailureRecorder failureRecorder,
            AgentProcessingTracker processingTracker
    ) {
        this.detectionEventRepository = detectionEventRepository;
        this.aiAnalysisService = aiAnalysisService;
        this.auditLogService = auditLogService;
        this.faultInjector = faultInjector;
        this.failureRecorder = failureRecorder;
        this.processingTracker = processingTracker;
    }

    @GetMapping
    public Page<DetectionEventResponse> getEvents(
            @RequestParam(required = false) EventType eventType,
            @RequestParam(required = false) RiskLevel riskLevel,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) String wallet,
            @RequestParam(required = false) String assignee,
            @RequestParam(required = false, defaultValue = "false") boolean unassigned,
            @RequestParam(required = false) String network,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(0, page), safeSize, Sort.by(Sort.Direction.DESC, "detectedAt"));
        return detectionEventRepository
                .search(eventType, riskLevel, status, wallet, assignee, unassigned, network, from, to, pageable)
                .map(DetectionEventResponse::from);
    }

    @GetMapping("/{id}")
    public DetectionEventDetailResponse getEvent(@PathVariable Long id) {
        DetectionEvent event = detectionEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Detection event not found: " + id));
        var report = aiAnalysisService.getReport(id);
        return DetectionEventDetailResponse.from(
                event,
                report != null ? AiAnalysisReportResponse.from(report) : null
        );
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public DetectionEventResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody EventStatusUpdateRequest request
    ) {
        DetectionEvent event = detectionEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Detection event not found: " + id));
        // 장애 주입 활성 시 실패를 기록(REQUIRES_NEW라 롤백과 무관하게 남음)하고 전이를 거부한다.
        if (faultInjector.isActive("triage")) {
            failureRecorder.record("triage",
                    "이벤트 #" + id + " 상태 전이 실패",
                    "장애 주입 활성 — " + event.getStatus() + " → " + request.status() + " 전이 거부", true);
            throw new ConflictException("장애 주입 활성 — Triage 팀 상태 전이가 강제 실패하도록 설정되어 있습니다.");
        }
        EventStatus previousStatus = event.getStatus();
        long startedNanos = System.nanoTime();
        event.applyStatusChange(
                request.status(),
                request.assignee(),
                request.resolutionReason(),
                request.falsePositiveReason(),
                request.notes()
        );
        auditLogService.record(
                AUDIT_ACTION_STATUS_CHANGE,
                AUDIT_TARGET_TYPE_EVENT,
                String.valueOf(id),
                previousStatus + " -> " + request.status()
                        + (request.assignee() != null ? ", assignee=" + request.assignee() : "")
        );
        processingTracker.record("triage", System.nanoTime() - startedNanos);
        return DetectionEventResponse.from(event);
    }
}
