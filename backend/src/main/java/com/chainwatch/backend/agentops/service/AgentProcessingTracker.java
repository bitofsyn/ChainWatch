package com.chainwatch.backend.agentops.service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * 팀별 작업 처리 시간을 1시간 롤링 윈도로 실측한다.
 * DB에 duration 컬럼을 둘 자연스러운 자리가 없는 작업(트랜잭션 스크리닝, 상태 전이)의
 * 평균 처리 시간을 제공하기 위한 인메모리 집계로, 프로세스 재시작 시 초기화된다.
 */
@Component
public class AgentProcessingTracker {

    private static final long WINDOW_MS = Duration.ofHours(1).toMillis();
    /** 고빈도 팀(초당 수십 건 스크리닝)의 메모리 상한. 초과 시 오래된 표본부터 버린다. */
    private static final int MAX_SAMPLES_PER_TEAM = 20_000;

    private record Sample(long recordedAtMs, long durationNanos) {
    }

    /** ConcurrentLinkedDeque.size()가 O(n)이라 근사 카운터를 함께 유지한다. */
    private static final class TeamSamples {
        private final ConcurrentLinkedDeque<Sample> deque = new ConcurrentLinkedDeque<>();
        private final AtomicInteger count = new AtomicInteger();
    }

    private final Map<String, TeamSamples> samplesByTeam = new ConcurrentHashMap<>();

    public void record(String teamId, long durationNanos) {
        TeamSamples samples = samplesByTeam.computeIfAbsent(teamId, key -> new TeamSamples());
        samples.deque.addLast(new Sample(System.currentTimeMillis(), durationNanos));
        samples.count.incrementAndGet();
        prune(samples);
    }

    /** 최근 1시간 평균 처리 시간(ms). 표본이 없으면 0(프론트 "-" 표기), 1ms 미만은 1ms로 올림. */
    public long averageMillis(String teamId) {
        TeamSamples samples = samplesByTeam.get(teamId);
        if (samples == null) {
            return 0;
        }
        prune(samples);
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        long count = 0;
        long totalNanos = 0;
        for (Sample sample : samples.deque) {
            if (sample.recordedAtMs() >= cutoff) {
                count++;
                totalNanos += sample.durationNanos();
            }
        }
        if (count == 0) {
            return 0;
        }
        return Math.max(1, Math.round(totalNanos / (double) count / 1_000_000.0));
    }

    private static void prune(TeamSamples samples) {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        Sample head;
        while ((head = samples.deque.peekFirst()) != null
                && (head.recordedAtMs() < cutoff || samples.count.get() > MAX_SAMPLES_PER_TEAM)) {
            if (samples.deque.pollFirst() != null) {
                samples.count.decrementAndGet();
            } else {
                break;
            }
        }
    }
}
