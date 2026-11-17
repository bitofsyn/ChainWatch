package com.chainwatch.backend.casework.api;

import com.chainwatch.backend.casework.service.CurrentActor;
import com.chainwatch.backend.event.api.DetectionEventResponse;
import com.chainwatch.backend.event.domain.EventStatus;
import com.chainwatch.backend.event.domain.RiskLevel;
import com.chainwatch.backend.event.repository.DetectionEventRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 분석가의 작업 큐. 로그인한 사람 기준으로 "내가 맡은 미완료 건"과 "아직 아무도 맡지 않은 고위험 건"을
 * 위험 점수가 높고 오래 대기한 순서로 돌려준다.
 */
@RestController
@RequestMapping("/api/workqueue")
public class WorkQueueController {

    private static final List<EventStatus> OPEN_STATUSES =
            List.of(EventStatus.NEW, EventStatus.ACKNOWLEDGED, EventStatus.INVESTIGATING);
    private static final List<RiskLevel> HIGH_RISK_LEVELS = List.of(RiskLevel.CRITICAL, RiskLevel.HIGH);
    private static final int LIST_SIZE = 15;

    private final DetectionEventRepository detectionEventRepository;

    public WorkQueueController(DetectionEventRepository detectionEventRepository) {
        this.detectionEventRepository = detectionEventRepository;
    }

    public record WorkQueueResponse(
            String username,
            long myOpenCount,
            long unassignedHighRiskCount,
            /** 미할당 고위험 중 가장 오래된 건의 탐지 시각. 없으면 null */
            Instant oldestUnassignedDetectedAt,
            List<DetectionEventResponse> myCases,
            List<DetectionEventResponse> unassignedHighRisk
    ) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public WorkQueueResponse getWorkQueue() {
        String username = CurrentActor.name();
        PageRequest top = PageRequest.of(0, LIST_SIZE);
        return new WorkQueueResponse(
                username,
                detectionEventRepository.countAssignedOpen(username, OPEN_STATUSES),
                detectionEventRepository.countUnassignedOpen(HIGH_RISK_LEVELS, OPEN_STATUSES),
                detectionEventRepository.findOldestUnassignedOpenDetectedAt(HIGH_RISK_LEVELS, OPEN_STATUSES),
                detectionEventRepository.findAssignedOpen(username, OPEN_STATUSES, top).stream()
                        .map(DetectionEventResponse::from).toList(),
                detectionEventRepository.findUnassignedOpen(HIGH_RISK_LEVELS, OPEN_STATUSES, top).stream()
                        .map(DetectionEventResponse::from).toList()
        );
    }
}
