package com.chainwatch.backend.casework.service;

import com.chainwatch.backend.audit.service.AuditLogService;
import com.chainwatch.backend.casework.domain.EventActivity;
import com.chainwatch.backend.casework.domain.PolicySuggestion;
import com.chainwatch.backend.casework.repository.EventActivityRepository;
import com.chainwatch.backend.casework.repository.PolicySuggestionRepository;
import com.chainwatch.backend.common.exception.ConflictException;
import com.chainwatch.backend.common.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 분석가의 탐지 예외 주소 제안과 관리자의 승인·반려·해제. */
@Service
public class PolicySuggestionService {

    private static final String AUDIT_TARGET_TYPE = "POLICY_SUGGESTION";

    private final PolicySuggestionRepository repository;
    private final EventActivityRepository activityRepository;
    private final ExcludedAddressRegistry registry;
    private final AuditLogService auditLogService;

    public PolicySuggestionService(
            PolicySuggestionRepository repository,
            EventActivityRepository activityRepository,
            ExcludedAddressRegistry registry,
            AuditLogService auditLogService
    ) {
        this.repository = repository;
        this.activityRepository = activityRepository;
        this.registry = registry;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public PolicySuggestion propose(String address, String reason, Long eventId) {
        String normalized = address.strip().toLowerCase(Locale.ROOT);
        if (repository.existsByAddressAndStatusIn(normalized,
                List.of(PolicySuggestion.STATUS_PENDING, PolicySuggestion.STATUS_ACCEPTED))) {
            throw new ConflictException("이 주소는 이미 제안되어 검토 중이거나 예외로 승인되어 있습니다.");
        }
        String actor = CurrentActor.name();
        PolicySuggestion saved = repository.save(new PolicySuggestion(normalized, reason.strip(), eventId, actor));
        if (eventId != null) {
            activityRepository.save(new EventActivity(eventId, actor, EventActivity.TYPE_COMMENT,
                    "탐지 예외 주소 제안: " + normalized + " · " + reason.strip()));
        }
        auditLogService.record("POLICY_SUGGESTION_CREATE", AUDIT_TARGET_TYPE, String.valueOf(saved.getId()),
                "address=" + normalized);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<PolicySuggestion> recent() {
        return repository.findTop200ByOrderByCreatedAtDesc();
    }

    /** decision: ACCEPT(승인) · REJECT(반려) · REVOKE(승인된 예외 해제) */
    @Transactional
    public PolicySuggestion decide(Long id, String decision, String note) {
        PolicySuggestion suggestion = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Policy suggestion not found: " + id));
        String current = suggestion.getStatus();
        String next = switch (decision == null ? "" : decision.toUpperCase(Locale.ROOT)) {
            case "ACCEPT" -> PolicySuggestion.STATUS_ACCEPTED;
            case "REJECT" -> PolicySuggestion.STATUS_REJECTED;
            case "REVOKE" -> PolicySuggestion.STATUS_REVOKED;
            default -> throw new IllegalArgumentException("decision must be ACCEPT, REJECT or REVOKE");
        };
        boolean allowed = PolicySuggestion.STATUS_REVOKED.equals(next)
                ? PolicySuggestion.STATUS_ACCEPTED.equals(current)
                : PolicySuggestion.STATUS_PENDING.equals(current);
        if (!allowed) {
            throw new ConflictException("현재 상태(" + current + ")에서는 이 결정을 내릴 수 없습니다.");
        }
        suggestion.decide(next, CurrentActor.name(), note);
        repository.saveAndFlush(suggestion);
        registry.reload();
        auditLogService.record("POLICY_SUGGESTION_" + decision.toUpperCase(Locale.ROOT), AUDIT_TARGET_TYPE,
                String.valueOf(id), "address=" + suggestion.getAddress());
        return suggestion;
    }
}
