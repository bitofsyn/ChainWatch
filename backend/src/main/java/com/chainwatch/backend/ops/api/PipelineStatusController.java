package com.chainwatch.backend.ops.api;

import com.chainwatch.backend.analysis.config.AiAnalysisProperties;
import com.chainwatch.backend.collector.config.CollectorProperties;
import com.chainwatch.backend.collector.service.BlockCollectionService;
import com.chainwatch.backend.detection.config.DetectionProperties;
import com.chainwatch.backend.event.repository.DetectionEventRepository;
import com.chainwatch.backend.notification.config.NotificationProperties;
import com.chainwatch.backend.ops.api.PipelineStatusResponse.ComponentStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

@RestController
@RequestMapping("/api/ops/pipeline")
public class PipelineStatusController {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(3);

    private final DetectionEventRepository detectionEventRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final KafkaAdmin kafkaAdmin;
    private final BlockCollectionService blockCollectionService;
    private final CollectorProperties collectorProperties;
    private final DetectionProperties detectionProperties;
    private final AiAnalysisProperties aiAnalysisProperties;
    private final NotificationProperties notificationProperties;
    private final WebClient.Builder webClientBuilder;

    public PipelineStatusController(
            DetectionEventRepository detectionEventRepository,
            StringRedisTemplate stringRedisTemplate,
            KafkaAdmin kafkaAdmin,
            BlockCollectionService blockCollectionService,
            CollectorProperties collectorProperties,
            DetectionProperties detectionProperties,
            AiAnalysisProperties aiAnalysisProperties,
            NotificationProperties notificationProperties,
            WebClient.Builder webClientBuilder
    ) {
        this.detectionEventRepository = detectionEventRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.kafkaAdmin = kafkaAdmin;
        this.blockCollectionService = blockCollectionService;
        this.collectorProperties = collectorProperties;
        this.detectionProperties = detectionProperties;
        this.aiAnalysisProperties = aiAnalysisProperties;
        this.notificationProperties = notificationProperties;
        this.webClientBuilder = webClientBuilder;
    }

    @GetMapping
    public PipelineStatusResponse getPipelineStatus() {
        List<ComponentStatus> components = new ArrayList<>();
        components.add(checkDatabase());
        components.add(checkRedis());
        components.add(checkKafka());
        components.add(checkAiServer());
        components.add(checkCollector());
        components.add(describeDetection());
        components.add(describeNotification());
        return new PipelineStatusResponse(Instant.now(), components);
    }

    private ComponentStatus checkDatabase() {
        try {
            long count = detectionEventRepository.count();
            return ComponentStatus.up("database", "PostgreSQL 연결 정상, 탐지 이벤트 " + count + "건 저장");
        } catch (RuntimeException exception) {
            return ComponentStatus.down("database", "DB 조회 실패: " + exception.getClass().getSimpleName());
        }
    }

    private ComponentStatus checkRedis() {
        try {
            String pong = stringRedisTemplate.execute(connection -> connection.ping(), true);
            if ("PONG".equalsIgnoreCase(pong)) {
                return ComponentStatus.up("redis", "Redis PING 응답 정상 (최근 피드 캐시)");
            }
            return ComponentStatus.down("redis", "Redis PING 응답 이상: " + pong);
        } catch (RuntimeException exception) {
            return ComponentStatus.down("redis", "Redis 연결 실패: " + exception.getClass().getSimpleName());
        }
    }

    private ComponentStatus checkKafka() {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            var cluster = adminClient.describeCluster(
                    new DescribeClusterOptions().timeoutMs((int) PROBE_TIMEOUT.toMillis()));
            int nodeCount = cluster.nodes().get(PROBE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).size();
            return ComponentStatus.up("kafka", "브로커 " + nodeCount + "대 응답 (raw-transactions/detected-events 토픽)");
        } catch (Exception exception) {
            return ComponentStatus.down("kafka", "브로커 연결 실패: " + exception.getClass().getSimpleName());
        }
    }

    private ComponentStatus checkAiServer() {
        if (!aiAnalysisProperties.enabled()) {
            return ComponentStatus.disabled("aiServer",
                    "AI 분석 비활성 (chainwatch.ai.enabled=false). 탐지 파이프라인은 AI 없이도 동작");
        }
        if (aiAnalysisProperties.baseUrl() == null || aiAnalysisProperties.baseUrl().isBlank()) {
            return ComponentStatus.down("aiServer", "chainwatch.ai.base-url 미설정");
        }
        try {
            webClientBuilder.baseUrl(aiAnalysisProperties.baseUrl()).build()
                    .get()
                    .uri("/health")
                    .retrieve()
                    .toBodilessEntity()
                    .block(PROBE_TIMEOUT);
            return ComponentStatus.up("aiServer",
                    aiAnalysisProperties.provider() + " 분석 서버 헬스 체크 정상 (" + aiAnalysisProperties.model() + ")");
        } catch (RuntimeException exception) {
            return ComponentStatus.down("aiServer", "분석 서버 응답 없음: " + aiAnalysisProperties.baseUrl());
        }
    }

    private ComponentStatus checkCollector() {
        long lastBlock = blockCollectionService.lastCollectedBlockNumber();
        String detail = String.format(
                "provider=%s, mode=%s, 마지막 수집 블록=%s, 재시도 최대 %d회, reorg 되감기 %d블록, 확정 기준 %d confirmations",
                collectorProperties.provider(),
                collectorProperties.mode(),
                lastBlock >= 0 ? lastBlock : "없음",
                collectorProperties.retry().maxAttempts(),
                collectorProperties.reorgRewindDepth(),
                collectorProperties.confirmationDepth()
        );
        if (!collectorProperties.enabled()) {
            return ComponentStatus.disabled("collector", "자동 수집 비활성 (수동 트리거만 가능). " + detail);
        }
        return ComponentStatus.up("collector", detail);
    }

    private ComponentStatus describeDetection() {
        String detail = String.format(
                "mode=%s, 대규모 이체 %s ETH↑, 거래소 플로우 %s ETH↑, 반복 이체 %d회/%d분",
                detectionProperties.mode(),
                detectionProperties.largeTransferThresholdEth(),
                detectionProperties.exchangeFlowThresholdEth(),
                detectionProperties.rapidTransferThresholdCount(),
                detectionProperties.rapidTransferWindowMinutes()
        );
        return ComponentStatus.up("detection", detail);
    }

    private ComponentStatus describeNotification() {
        boolean slackConfigured = notificationProperties.slackWebhookUrl() != null
                && !notificationProperties.slackWebhookUrl().isBlank();
        boolean discordConfigured = notificationProperties.discordWebhookUrl() != null
                && !notificationProperties.discordWebhookUrl().isBlank();
        String detail = String.format(
                "위험 점수 %d점 이상 알림, 중복 억제 %d분, Slack %s, Discord %s",
                notificationProperties.minRiskScore(),
                notificationProperties.dedupTtlMinutes(),
                slackConfigured ? "연결됨" : "미설정",
                discordConfigured ? "연결됨" : "미설정"
        );
        if (!notificationProperties.enabled()) {
            return ComponentStatus.disabled("notification", "알림 비활성. " + detail);
        }
        return ComponentStatus.up("notification", detail);
    }
}
