package com.chainwatch.backend.event.api;

import com.chainwatch.backend.event.repository.DetectionEventRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class DetectionEventController {

    private final DetectionEventRepository detectionEventRepository;

    public DetectionEventController(DetectionEventRepository detectionEventRepository) {
        this.detectionEventRepository = detectionEventRepository;
    }

    @GetMapping
    public List<DetectionEventResponse> getEvents() {
        return detectionEventRepository.findAll()
                .stream()
                .map(DetectionEventResponse::from)
                .toList();
    }
}
