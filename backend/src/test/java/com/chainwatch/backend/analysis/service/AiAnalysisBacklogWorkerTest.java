package com.chainwatch.backend.analysis.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chainwatch.backend.analysis.config.AiAnalysisProperties;
import com.chainwatch.backend.analysis.domain.AiAnalysisReport;
import com.chainwatch.backend.analysis.domain.AnalysisStatus;
import com.chainwatch.backend.analysis.repository.AiAnalysisReportRepository;
import com.chainwatch.backend.event.domain.DetectionEvent;
import com.chainwatch.backend.event.repository.DetectionEventRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiAnalysisBacklogWorkerTest {

    @Mock
    private DetectionEventRepository detectionEventRepository;

    @Mock
    private AiAnalysisReportRepository aiAnalysisReportRepository;

    @Mock
    private AiAnalysisService aiAnalysisService;

    @Mock
    private AsyncAiAnalysisRunner asyncAiAnalysisRunner;

    @Test
    void doesNothingWhenWorkerDisabled() {
        AiAnalysisBacklogWorker worker = worker(true, false);

        worker.drain();

        verifyNoInteractions(detectionEventRepository, aiAnalysisReportRepository,
                aiAnalysisService, asyncAiAnalysisRunner);
    }

    @Test
    void doesNothingWhenAiDisabled() {
        AiAnalysisBacklogWorker worker = worker(false, true);

        worker.drain();

        verifyNoInteractions(detectionEventRepository, aiAnalysisReportRepository,
                aiAnalysisService, asyncAiAnalysisRunner);
    }

    @Test
    void submitsOldestPendingEventsAsPendingThenRuns() {
        AiAnalysisBacklogWorker worker = worker(true, true);
        List<DetectionEvent> batch = List.of(event(1L), event(2L));
        when(detectionEventRepository.findPendingAnalysisOldestFirst(anyCollection(), any()))
                .thenReturn(batch);
        when(aiAnalysisReportRepository
                .findTop3ByStatusAndAnalyzedAtBeforeOrderByAnalyzedAtAsc(eq(AnalysisStatus.PENDING), any()))
                .thenReturn(List.of());

        worker.drain();

        verify(aiAnalysisService).markPending(1L);
        verify(asyncAiAnalysisRunner).run(1L);
        verify(aiAnalysisService).markPending(2L);
        verify(asyncAiAnalysisRunner).run(2L);
    }

    @Test
    void resubmitsStalePendingReports() {
        AiAnalysisBacklogWorker worker = worker(true, true);
        when(detectionEventRepository.findPendingAnalysisOldestFirst(anyCollection(), any()))
                .thenReturn(List.of());
        DetectionEvent staleEvent = event(9L);
        AiAnalysisReport stale = new AiAnalysisReport(
                staleEvent, AnalysisStatus.PENDING, "", "", "prompt", "AI 분석이 진행 중입니다.", null, Instant.now());
        when(aiAnalysisReportRepository
                .findTop3ByStatusAndAnalyzedAtBeforeOrderByAnalyzedAtAsc(eq(AnalysisStatus.PENDING), any()))
                .thenReturn(List.of(stale));

        worker.drain();

        verify(aiAnalysisService).markPending(9L);
        verify(asyncAiAnalysisRunner).run(9L);
    }

    private DetectionEvent event(Long id) {
        DetectionEvent event = org.mockito.Mockito.mock(DetectionEvent.class);
        when(event.getId()).thenReturn(id);
        return event;
    }

    private AiAnalysisBacklogWorker worker(boolean aiEnabled, boolean workerEnabled) {
        AiAnalysisProperties properties = new AiAnalysisProperties(
                aiEnabled, "", "", "", "/api/v1/analyze",
                new AiAnalysisProperties.Worker(workerEnabled, 30_000, 3, 10));
        return new AiAnalysisBacklogWorker(properties, detectionEventRepository,
                aiAnalysisReportRepository, aiAnalysisService, asyncAiAnalysisRunner);
    }
}
