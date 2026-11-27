package com.chainwatch.backend.event.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.chainwatch.backend.analysis.domain.AiAnalysisReport;
import com.chainwatch.backend.analysis.domain.AnalysisStatus;
import com.chainwatch.backend.event.domain.DetectionEvent;
import com.chainwatch.backend.event.domain.EventStatus;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

/** 자동 분석 워커가 고르는 대상과 순서. */
@DataJpaTest
class PendingAnalysisSelectionTest {

    private static final List<RiskLevel> TARGET_LEVELS = List.of(RiskLevel.CRITICAL, RiskLevel.HIGH);
    private static final Instant BASE = Instant.parse("2026-11-27T00:00:00Z");

    @Autowired
    private DetectionEventRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void picksOpenUnanalyzedHighRiskEventsByScoreThenNewest() {
        DetectionEvent oldHigh = save(event(RiskLevel.HIGH, 85, BASE));
        DetectionEvent newHigh = save(event(RiskLevel.HIGH, 85, BASE.plusSeconds(3600)));
        DetectionEvent critical = save(event(RiskLevel.CRITICAL, 100, BASE.plusSeconds(60)));
        save(event(RiskLevel.MEDIUM, 72, BASE.plusSeconds(7200)));

        DetectionEvent resolved = event(RiskLevel.HIGH, 90, BASE.plusSeconds(7200));
        resolved.applyStatusChange(EventStatus.RESOLVED, null, "확인 완료", null, null);
        save(resolved);

        DetectionEvent analyzed = save(event(RiskLevel.HIGH, 95, BASE.plusSeconds(7200)));
        entityManager.persist(new AiAnalysisReport(
                analyzed, AnalysisStatus.COMPLETED, "gemini", "model", "prompt", "report", "{}", BASE));
        entityManager.flush();

        List<DetectionEvent> picked = repository
                .findPendingAnalysisHighestRiskNewestFirst(TARGET_LEVELS, PageRequest.of(0, 10));

        assertThat(picked).extracting(DetectionEvent::getId)
                .containsExactly(critical.getId(), newHigh.getId(), oldHigh.getId());
        assertThat(repository.countPendingAnalysis(TARGET_LEVELS)).isEqualTo(3);
    }

    private DetectionEvent save(DetectionEvent event) {
        return entityManager.persistAndFlush(event);
    }

    private static DetectionEvent event(RiskLevel level, int score, Instant detectedAt) {
        return new DetectionEvent(
                EventType.LARGE_TRANSFER, level, score, "요약", "0xwallet", detectedAt, null);
    }
}
