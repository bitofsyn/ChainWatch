package com.chainwatch.backend.event.api;

import com.chainwatch.backend.analysis.api.AiAnalysisReportResponse;
import com.chainwatch.backend.analysis.service.AiAnalysisService;
import com.chainwatch.backend.audit.service.AuditLogService;
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

    public DetectionEventController(
            DetectionEventRepository detectionEventRepository,
            AiAnalysisService aiAnalysisService,
            AuditLogService auditLogService
    ) {
        this.detectionEventRepository = detectionEventRepository;
        this.aiAnalysisService = aiAnalysisService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public Page<DetectionEventResponse> getEvents(
            @RequestParam(required = false) EventType eventType,
            @RequestParam(required = false) RiskLevel riskLevel,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) String wallet,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(0, page), safeSize, Sort.by(Sort.Direction.DESC, "detectedAt"));
        return detectionEventRepository.search(eventType, riskLevel, status, wallet, from, to, pageable)
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
        EventStatus previousStatus = event.getStatus();
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
        return DetectionEventResponse.from(event);
    }
}
