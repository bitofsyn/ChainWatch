package com.chainwatch.backend.casework.api;

import com.chainwatch.backend.casework.domain.EventActivity;
import com.chainwatch.backend.casework.service.EventWorkflowService;
import com.chainwatch.backend.event.api.EventStatusUpdateRequest;
import com.chainwatch.backend.event.domain.EventStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 이벤트 처리 이력 조회, 코멘트 추가, 일괄 상태 변경. */
@RestController
@RequestMapping("/api/events")
public class EventCaseworkController {

    private final EventWorkflowService workflowService;

    public EventCaseworkController(EventWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    public record ActivityResponse(Long id, String actor, String type, String detail, Instant createdAt) {
        static ActivityResponse from(EventActivity activity) {
            return new ActivityResponse(activity.getId(), activity.getActor(), activity.getType(),
                    activity.getDetail(), activity.getCreatedAt());
        }
    }

    public record CommentRequest(@NotBlank @Size(max = 2000) String text) {
    }

    public record BulkStatusRequest(
            @NotEmpty @Size(max = EventWorkflowService.MAX_BULK_SIZE) List<Long> ids,
            @NotNull EventStatus status,
            @Size(max = 100) String assignee,
            @Size(max = 500) String resolutionReason,
            @Size(max = 500) String falsePositiveReason,
            @Size(max = 2000) String notes
    ) {
    }

    public record BulkStatusResponse(int updated, List<Long> missingIds) {
    }

    @GetMapping("/{id}/activities")
    public List<ActivityResponse> activities(@PathVariable Long id) {
        return workflowService.activities(id).stream().map(ActivityResponse::from).toList();
    }

    @PostMapping("/{id}/comments")
    public ActivityResponse addComment(@PathVariable Long id, @Valid @RequestBody CommentRequest request) {
        return ActivityResponse.from(workflowService.addComment(id, request.text()));
    }

    @PostMapping("/bulk-status")
    public BulkStatusResponse bulkStatus(@Valid @RequestBody BulkStatusRequest request) {
        EventWorkflowService.BulkResult result = workflowService.changeStatusInBulk(
                request.ids(),
                new EventStatusUpdateRequest(request.status(), request.assignee(), request.resolutionReason(),
                        request.falsePositiveReason(), request.notes()));
        return new BulkStatusResponse(result.updated(), result.missingIds());
    }
}
