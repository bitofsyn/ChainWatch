package com.chainwatch.backend.agentops.service;

import com.chainwatch.backend.agentops.api.AgentFaultStatusResponse;
import com.chainwatch.backend.agentops.repository.AgentTaskFailureRepository;
import com.chainwatch.backend.agentops.service.AgentFaultInjector.FaultState;
import com.chainwatch.backend.common.exception.ResourceNotFoundException;
import com.chainwatch.backend.notification.domain.NotificationHistory;
import com.chainwatch.backend.notification.repository.NotificationHistoryRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팀별 예외 시나리오(장애 주입)를 켜고 끈다.
 * 활성화 즉시 드릴 실패 샘플을 실제 실패 저장 경로(agent_task_failures, notification_history)에
 * 기록해, 라이브 트래픽이 없는 환경에서도 다음 콘솔 폴링에 실패·성공률 하락이 바로 반영된다.
 * 활성 상태가 유지되는 동안에는 각 팀의 실제 처리 경로가 훅에서 강제 실패한다.
 */
@Service
public class AgentFaultService {

    public static final String DRILL_CHANNEL = "drill";

    private static final Logger log = LoggerFactory.getLogger(AgentFaultService.class);
    private static final int DEFAULT_DRILL_COUNT = 3;
    private static final int MAX_DRILL_COUNT = 10;

    /** teamId → 주입 시 적용되는 예외 시나리오 설명. 키 순서가 콘솔 노출 순서다. */
    private static final Map<String, String> SCENARIOS = new LinkedHashMap<>();

    static {
        SCENARIOS.put("detection", "트랜잭션 스크리닝 강제 실패 — 룰 평가가 실패 처리되고 이벤트가 생성되지 않음");
        SCENARIOS.put("analysis", "AI 분석 강제 실패 — 분석 요청이 FAILED 리포트로 기록됨");
        SCENARIOS.put("triage", "이벤트 상태 전이 강제 실패 — lifecycle 변경 요청이 409로 거부됨");
        SCENARIOS.put("notification", "알림 발송 강제 실패 — 웹훅 발송이 실패 이력으로 기록됨");
        SCENARIOS.put("ops", "에스컬레이션 처리 실패 — 파이프라인 장애 대응 작업 실패 기록");
    }

    private final AgentFaultInjector faultInjector;
    private final AgentFailureRecorder failureRecorder;
    private final AgentTaskFailureRepository failureRepository;
    private final NotificationHistoryRepository notificationHistoryRepository;

    public AgentFaultService(
            AgentFaultInjector faultInjector,
            AgentFailureRecorder failureRecorder,
            AgentTaskFailureRepository failureRepository,
            NotificationHistoryRepository notificationHistoryRepository
    ) {
        this.faultInjector = faultInjector;
        this.failureRecorder = failureRecorder;
        this.failureRepository = failureRepository;
        this.notificationHistoryRepository = notificationHistoryRepository;
    }

    public AgentFaultStatusResponse activate(String teamId, long ttlSeconds, int drillCount) {
        String scenario = requireScenario(teamId);
        FaultState state = faultInjector.activate(teamId, scenario, ttlSeconds);
        drill(teamId, Math.max(0, Math.min(drillCount, MAX_DRILL_COUNT)));
        log.info("agent fault activated | teamId={} expiresAt={} drillCount={}",
                teamId, state.expiresAt(), drillCount);
        return toResponse(teamId);
    }

    @Transactional
    public AgentFaultStatusResponse clear(String teamId, boolean purgeRecords) {
        requireScenario(teamId);
        faultInjector.clear(teamId);
        if (purgeRecords) {
            long removed = failureRepository.deleteByTeamIdAndInjectedTrue(teamId);
            long removedHistory = 0;
            if ("notification".equals(teamId)) {
                removedHistory = notificationHistoryRepository.deleteByChannel(DRILL_CHANNEL);
            }
            log.info("agent fault cleared | teamId={} purgedFailures={} purgedHistory={}",
                    teamId, removed, removedHistory);
        } else {
            log.info("agent fault cleared | teamId={}", teamId);
        }
        return toResponse(teamId);
    }

    public List<AgentFaultStatusResponse> statuses() {
        return SCENARIOS.keySet().stream().map(this::toResponse).toList();
    }

    /** 활성화 직후 실패 샘플을 기록해 트래픽이 없어도 콘솔에서 즉시 예외 상황을 확인할 수 있게 한다. */
    private void drill(String teamId, int count) {
        for (int i = 0; i < count; i++) {
            switch (teamId) {
                case "detection" -> failureRecorder.record(teamId,
                        "트랜잭션 스크리닝 실패 (장애 주입)",
                        "시뮬레이션: RPC 응답 타임아웃으로 룰 평가 실패", true);
                case "analysis" -> failureRecorder.record(teamId,
                        "AI 분석 실패 (장애 주입)",
                        "시뮬레이션: LLM 호출 타임아웃 — 리포트 생성 실패", true);
                case "triage" -> failureRecorder.record(teamId,
                        "이벤트 상태 전이 실패 (장애 주입)",
                        "시뮬레이션: 낙관적 락 충돌로 lifecycle 전이 롤백", true);
                case "notification" -> notificationHistoryRepository.save(new NotificationHistory(
                        null, "FAULT_DRILL", 0, DRILL_CHANNEL, false,
                        "시뮬레이션: webhook 5xx 응답 — 발송 실패", Instant.now()));
                case "ops" -> failureRecorder.record(teamId,
                        "에스컬레이션 처리 실패 (장애 주입)",
                        "시뮬레이션: 운영자 페이징 채널 응답 없음", true);
                default -> {
                }
            }
        }
    }

    private AgentFaultStatusResponse toResponse(String teamId) {
        return faultInjector.state(teamId)
                .map(state -> new AgentFaultStatusResponse(
                        teamId, true, SCENARIOS.get(teamId), state.activatedAt(), state.expiresAt()))
                .orElseGet(() -> new AgentFaultStatusResponse(
                        teamId, false, SCENARIOS.get(teamId), null, null));
    }

    private static String requireScenario(String teamId) {
        String scenario = SCENARIOS.get(teamId);
        if (scenario == null) {
            throw new ResourceNotFoundException("agent team not found: " + teamId);
        }
        return scenario;
    }
}
