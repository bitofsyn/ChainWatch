package com.chainwatch.backend.event.api;

import com.chainwatch.backend.event.repository.DetectionEventRepository;
import com.chainwatch.backend.event.domain.DetectionEvent;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class DetectionEventController {

    private final DetectionEventRepository detectionEventRepository;

    public DetectionEventController(DetectionEventRepository detectionEventRepository) {
        this.detectionEventRepository = detectionEventRepository;
    }

    @GetMapping
    public Page<DetectionEventResponse> getEvents(
            @RequestParam(required = false) EventType eventType,
            @RequestParam(required = false) RiskLevel riskLevel,
            @RequestParam(required = false) String wallet,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "detectedAt"));
        return detectionEventRepository.search(eventType, riskLevel, wallet, from, to, pageable)
                .map(DetectionEventResponse::from);
    }

    @GetMapping("/{id}")
    public DetectionEventDetailResponse getEvent(@PathVariable Long id) {
        DetectionEvent event = detectionEventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Detection event not found: " + id));
        return DetectionEventDetailResponse.from(event);
    }
}
