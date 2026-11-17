package com.chainwatch.backend.casework.api;

import com.chainwatch.backend.casework.domain.PolicySuggestion;
import com.chainwatch.backend.casework.service.PolicySuggestionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 탐지 예외 주소 제안. 분석가·관리자가 제안하고 목록을 보며,
 * 결정(/api/admin 하위)은 관리자만 내린다.
 */
@RestController
public class PolicySuggestionController {

    private final PolicySuggestionService service;

    public PolicySuggestionController(PolicySuggestionService service) {
        this.service = service;
    }

    public record SuggestionResponse(
            Long id, String address, String reason, Long eventId, String proposedBy, String status,
            String decidedBy, String decisionNote, Instant createdAt, Instant decidedAt
    ) {
        static SuggestionResponse from(PolicySuggestion s) {
            return new SuggestionResponse(s.getId(), s.getAddress(), s.getReason(), s.getEventId(),
                    s.getProposedBy(), s.getStatus(), s.getDecidedBy(), s.getDecisionNote(),
                    s.getCreatedAt(), s.getDecidedAt());
        }
    }

    public record ProposeRequest(
            @NotBlank @Pattern(regexp = "^0x[0-9a-fA-F]{40}$", message = "0x로 시작하는 40자리 주소여야 합니다.")
            String address,
            @NotBlank @Size(max = 500) String reason,
            Long eventId
    ) {
    }

    public record DecisionRequest(@NotBlank String decision, @Size(max = 500) String note) {
    }

    @GetMapping("/api/policy-suggestions")
    public List<SuggestionResponse> list() {
        return service.recent().stream().map(SuggestionResponse::from).toList();
    }

    @PostMapping("/api/policy-suggestions")
    public SuggestionResponse propose(@Valid @RequestBody ProposeRequest request) {
        return SuggestionResponse.from(service.propose(request.address(), request.reason(), request.eventId()));
    }

    @PatchMapping("/api/admin/policy-suggestions/{id}")
    public SuggestionResponse decide(@PathVariable Long id, @Valid @RequestBody DecisionRequest request) {
        return SuggestionResponse.from(service.decide(id, request.decision(), request.note()));
    }
}
