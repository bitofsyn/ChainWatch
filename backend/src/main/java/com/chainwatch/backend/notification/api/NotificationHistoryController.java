package com.chainwatch.backend.notification.api;

import com.chainwatch.backend.notification.repository.NotificationHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications/history")
public class NotificationHistoryController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationHistoryRepository repository;

    public NotificationHistoryController(NotificationHistoryRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public Page<NotificationHistoryResponse> getHistory(
            @RequestParam(required = false) Long eventId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE));
        if (eventId != null) {
            return repository.findByEventIdOrderBySentAtDesc(eventId, pageable)
                    .map(NotificationHistoryResponse::from);
        }
        return repository.findAllByOrderBySentAtDesc(pageable).map(NotificationHistoryResponse::from);
    }
}
