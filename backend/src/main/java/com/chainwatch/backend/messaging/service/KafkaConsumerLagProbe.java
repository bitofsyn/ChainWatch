package com.chainwatch.backend.messaging.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

/**
 * 컨슈머 그룹의 토픽 랙(미소비 메시지 수)을 조회한다. Agent 콘솔의 "대기 큐"를
 * 추정치가 아닌 실제 Kafka 오프셋 차이로 채우기 위한 프로브.
 * 스냅샷 폴링(15초)마다 브로커를 두드리지 않도록 그룹·토픽별 10초 캐시를 둔다.
 * Kafka 미기동/타임아웃 등 조회 실패 시 값을 지어내지 않고 empty를 반환한다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.kafka", name = "bootstrap-servers")
public class KafkaConsumerLagProbe implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerLagProbe.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(10);
    private static final long PROBE_TIMEOUT_SECONDS = 2;

    private record CachedLag(OptionalLong lag, Instant probedAt) {
    }

    private final KafkaAdmin kafkaAdmin;
    private final Map<String, CachedLag> cache = new ConcurrentHashMap<>();
    private volatile AdminClient adminClient;

    public KafkaConsumerLagProbe(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    public OptionalLong consumerLag(String groupId, String topic) {
        String key = groupId + "|" + topic;
        CachedLag cached = cache.get(key);
        if (cached != null && cached.probedAt().plus(CACHE_TTL).isAfter(Instant.now())) {
            return cached.lag();
        }
        OptionalLong lag = probe(groupId, topic);
        cache.put(key, new CachedLag(lag, Instant.now()));
        return lag;
    }

    private OptionalLong probe(String groupId, String topic) {
        try {
            AdminClient client = client();
            Map<TopicPartition, OffsetAndMetadata> committed = client
                    .listConsumerGroupOffsets(groupId)
                    .partitionsToOffsetAndMetadata()
                    .get(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .entrySet().stream()
                    .filter(entry -> entry.getKey().topic().equals(topic) && entry.getValue() != null)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            if (committed.isEmpty()) {
                // 그룹이 아직 이 토픽을 소비한 적 없음 — 랙을 판정할 수 없다.
                return OptionalLong.empty();
            }
            Map<TopicPartition, OffsetSpec> latestSpec = committed.keySet().stream()
                    .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> ends = client
                    .listOffsets(latestSpec)
                    .all()
                    .get(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            long lag = 0;
            for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : committed.entrySet()) {
                ListOffsetsResult.ListOffsetsResultInfo end = ends.get(entry.getKey());
                if (end != null) {
                    lag += Math.max(0, end.offset() - entry.getValue().offset());
                }
            }
            return OptionalLong.of(lag);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return OptionalLong.empty();
        } catch (Exception exception) {
            log.debug("consumer lag probe failed | groupId={} topic={} error={}",
                    groupId, topic, exception.getMessage());
            return OptionalLong.empty();
        }
    }

    private AdminClient client() {
        AdminClient current = adminClient;
        if (current == null) {
            synchronized (this) {
                if (adminClient == null) {
                    adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties());
                }
                current = adminClient;
            }
        }
        return current;
    }

    @Override
    public void destroy() {
        AdminClient current = adminClient;
        if (current != null) {
            current.close(Duration.ofSeconds(2));
        }
    }
}
