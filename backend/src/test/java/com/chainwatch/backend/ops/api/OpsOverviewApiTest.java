package com.chainwatch.backend.ops.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainwatch.backend.collector.domain.CollectorState;
import com.chainwatch.backend.collector.repository.CollectorStateRepository;
import com.chainwatch.backend.event.domain.DetectionEvent;
import com.chainwatch.backend.event.domain.EventStatus;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.event.domain.RiskLevel;
import com.chainwatch.backend.event.repository.DetectionEventRepository;
import com.chainwatch.backend.transaction.domain.Transaction;
import com.chainwatch.backend.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GET /api/ops/overview 계약 검증: 0건/분모 0/UNKNOWN 처리, 시계열·매트릭스 집계,
 * range/bucket allowlist 검증을 실제 H2 저장소 위에서 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpsOverviewApiTest {

    /** CollectedBlockProcessor.COLLECTOR_NAME(패키지 전용)과 동일한 상태 행 키 */
    private static final String COLLECTOR_NAME = "ethereum-main-collector";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private DetectionEventRepository detectionEventRepository;

    @Autowired
    private CollectorStateRepository collectorStateRepository;

    @AfterEach
    void cleanUp() {
        detectionEventRepository.deleteAll();
        transactionRepository.deleteAll();
        collectorStateRepository.deleteAll();
    }

    @Test
    void emptyDatabaseReturnsUnknownsInsteadOfFakeZeros() throws Exception {
        mockMvc.perform(get("/api/ops/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("24h"))
                .andExpect(jsonPath("$.bucket").value("1h"))
                .andExpect(jsonPath("$.collector.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.collector.chainHead").value(nullValue()))
                .andExpect(jsonPath("$.collector.lagBlocks").value(nullValue()))
                .andExpect(jsonPath("$.kpis.transactionsPerMinute").value(0.0))
                // 직전 구간 0건 → 증감률 비교 불가
                .andExpect(jsonPath("$.kpis.transactionsDeltaPercent").value(nullValue()))
                // 수집 0건(분모 0) → 탐지율 null (0%로 거짓 표시 금지)
                .andExpect(jsonPath("$.kpis.detectionRatePercent").value(nullValue()))
                .andExpect(jsonPath("$.kpis.backlogCount").value(0))
                .andExpect(jsonPath("$.kpis.oldestBacklogAgeSeconds").value(nullValue()))
                // DLT 카운터 미등록(Kafka 미기동) → null
                .andExpect(jsonPath("$.kpis.dltCount").value(nullValue()))
                .andExpect(jsonPath("$.series.length()").value(24))
                .andExpect(jsonPath("$.series[0].collectedTransactions").value(0))
                .andExpect(jsonPath("$.series[0].detectionRatePercent").value(nullValue()))
                .andExpect(jsonPath("$.riskStatusMatrix.length()").value(0))
                .andExpect(jsonPath("$.eventTypes.length()").value(0));
    }

    @Test
    void aggregatesKpisSeriesAndMatrixFromSeededData() throws Exception {
        Instant now = Instant.now();

        // KPI 창: 최근 5분 수집 2건, 직전 5분 1건 → 증감률 +100%
        saveTransaction("0xtx-recent-1", now.minus(Duration.ofMinutes(1)));
        saveTransaction("0xtx-recent-2", now.minus(Duration.ofMinutes(2)));
        saveTransaction("0xtx-previous", now.minus(Duration.ofMinutes(7)));

        // 최근 5분 탐지 1건 → 탐지율 1/2 = 50%
        DetectionEvent recent = saveEvent(EventType.LARGE_TRANSFER, RiskLevel.CRITICAL,
                now.minus(Duration.ofMinutes(3)));

        // 매트릭스: null status(레거시)는 NEW로 합산, ACKNOWLEDGED는 backlog 포함, RESOLVED는 제외
        DetectionEvent legacyNew = saveEvent(EventType.LARGE_TRANSFER, RiskLevel.CRITICAL,
                now.minus(Duration.ofHours(2)));
        DetectionEvent acknowledged = saveEvent(EventType.FAN_OUT, RiskLevel.HIGH,
                now.minus(Duration.ofHours(3)));
        acknowledged.changeStatus(EventStatus.ACKNOWLEDGED);
        detectionEventRepository.save(acknowledged);
        DetectionEvent resolved = saveEvent(EventType.RAPID_TRANSFER, RiskLevel.MEDIUM,
                now.minus(Duration.ofHours(4)));
        resolved.changeStatus(EventStatus.RESOLVED);
        detectionEventRepository.save(resolved);

        mockMvc.perform(get("/api/ops/overview").param("range", "24h").param("bucket", "1h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis.transactionsPerMinute").value(0.4))
                .andExpect(jsonPath("$.kpis.transactionsDeltaPercent").value(100.0))
                .andExpect(jsonPath("$.kpis.detectionRatePercent").value(50.0))
                .andExpect(jsonPath("$.kpis.detectedLast5m").value(1))
                // backlog = NEW(recent, legacyNew) + ACKNOWLEDGED = 3 (RESOLVED 제외)
                .andExpect(jsonPath("$.kpis.backlogCount").value(3))
                // 가장 오래된 backlog는 3시간 전 ACKNOWLEDGED
                .andExpect(jsonPath("$.kpis.oldestBacklogAgeSeconds").value(greaterThanOrEqualTo(10700)))
                // 시계열 버킷 수 = 24 (빈 버킷 0 채움 포함, 합계는 아래에서 별도 검증)
                .andExpect(jsonPath("$.series.length()").value(24))
                // 마지막 버킷만 집계 중(partial), 과거 버킷은 완료
                .andExpect(jsonPath("$.series[0].partial").value(false))
                .andExpect(jsonPath("$.series[23].partial").value(true))
                // 매트릭스: CRITICAL×NEW=2(null 합산), HIGH×ACKNOWLEDGED=1, MEDIUM×RESOLVED=1
                .andExpect(jsonPath("$.riskStatusMatrix.length()").value(3))
                .andExpect(jsonPath(
                        "$.riskStatusMatrix[?(@.riskLevel=='CRITICAL' && @.status=='NEW')].count",
                        contains(2)))
                .andExpect(jsonPath(
                        "$.riskStatusMatrix[?(@.riskLevel=='HIGH' && @.status=='ACKNOWLEDGED')].count",
                        contains(1)))
                // 기간 내 유형 집계: LARGE_TRANSFER 2건이 1위
                .andExpect(jsonPath("$.eventTypes[0].key").value("LARGE_TRANSFER"))
                .andExpect(jsonPath("$.eventTypes[0].count").value(2));

        // 시계열 합계 = 24시간 내 전체 건수 (버킷 경계와 무관하게 총량 보존 검증)
        String body = mockMvc.perform(get("/api/ops/overview").param("range", "24h").param("bucket", "1h"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode series =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("series");
        long collectedSum = 0;
        long detectedSum = 0;
        for (com.fasterxml.jackson.databind.JsonNode point : series) {
            collectedSum += point.get("collectedTransactions").asLong();
            detectedSum += point.get("detectedEvents").asLong();
        }
        org.junit.jupiter.api.Assertions.assertEquals(3, collectedSum);
        org.junit.jupiter.api.Assertions.assertEquals(4, detectedSum);

        // 상태 저장소가 head를 관측하면 lag 기반 상태를 내려준다
        CollectorState state = new CollectorState(COLLECTOR_NAME, 1000L, now);
        state.observeChainHead(1010L);
        collectorStateRepository.save(state);

        mockMvc.perform(get("/api/ops/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collector.chainHead").value(1010))
                .andExpect(jsonPath("$.collector.lastCollectedBlock").value(1000))
                .andExpect(jsonPath("$.collector.lagBlocks").value(10))
                .andExpect(jsonPath("$.collector.status").value("UP"));

        // 사용된 이벤트 변수 경고 방지용 아님: 탐지 유형 집계에 recent/legacyNew가 반영됐는지 위에서 검증됨
        org.junit.jupiter.api.Assertions.assertNotNull(recent.getId());
        org.junit.jupiter.api.Assertions.assertNotNull(legacyNew.getId());
    }

    @Test
    void rejectsRangeAndBucketOutsideAllowlist() throws Exception {
        mockMvc.perform(get("/api/ops/overview").param("range", "7d"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/ops/overview").param("bucket", "2m"))
                .andExpect(status().isBadRequest());

        // 24h/5m = 288 버킷 → 과밀 조합 거부
        mockMvc.perform(get("/api/ops/overview").param("range", "24h").param("bucket", "5m"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void degradedAndDownStatusFollowLagThresholds() throws Exception {
        CollectorState state = new CollectorState(COLLECTOR_NAME, 1000L, Instant.now());
        state.observeChainHead(1100L); // lag 100 → DEGRADED
        collectorStateRepository.save(state);

        mockMvc.perform(get("/api/ops/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collector.status").value("DEGRADED"));

        state.observeChainHead(2000L); // lag 1000 → DOWN
        collectorStateRepository.save(state);

        mockMvc.perform(get("/api/ops/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collector.status").value("DOWN"));
    }

    private void saveTransaction(String txHash, Instant timestamp) {
        transactionRepository.save(new Transaction(
                txHash,
                "0xfrom",
                "0xto",
                new BigDecimal("1.5"),
                new BigDecimal("0.001"),
                100L,
                timestamp,
                null
        ));
    }

    private DetectionEvent saveEvent(EventType eventType, RiskLevel riskLevel, Instant detectedAt) {
        return detectionEventRepository.save(new DetectionEvent(
                eventType,
                riskLevel,
                90,
                "test event",
                "0xwallet",
                detectedAt,
                null
        ));
    }
}
