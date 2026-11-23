package com.chainwatch.backend.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainwatch.backend.event.repository.DetectionEventRepository;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventStatsServiceTest {

    @Mock
    private DetectionEventRepository repository;

    @Test
    void cachesRepeatedStatsRequestsWithinTtl() {
        when(repository.count()).thenReturn(42L);
        when(repository.countByDetectedAtAfter(org.mockito.ArgumentMatchers.any())).thenReturn(7L);
        when(repository.countGroupByRiskLevel()).thenReturn(List.of());
        when(repository.countGroupByEventType()).thenReturn(List.of());
        when(repository.countGroupByStatus()).thenReturn(List.of());
        when(repository.findTopWalletsByEventCount(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        EventStatsService service = new EventStatsService(repository, Duration.ofSeconds(10));

        assertThat(service.getStats().totalEvents()).isEqualTo(42L);
        assertThat(service.getStats().totalEvents()).isEqualTo(42L);

        verify(repository).count();
    }
}
