package com.chainwatch.backend.agentops.service;

import com.chainwatch.backend.agentops.domain.AgentTaskFailure;
import com.chainwatch.backend.agentops.repository.AgentTaskFailureRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agent 팀 실패 이력을 기록한다.
 * REQUIRES_NEW로 별도 트랜잭션에 커밋해, 실패를 유발한 호출 트랜잭션이 롤백되어도
 * (예: 장애 주입으로 상태 전이가 롤백되는 경우) 실패 기록은 남는다.
 * 기록 실패가 원 처리 흐름을 깨지 않도록 예외는 삼키고 로그만 남긴다.
 */
@Component
public class AgentFailureRecorder {

    private static final Logger log = LoggerFactory.getLogger(AgentFailureRecorder.class);
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_DETAIL_LENGTH = 500;

    private final AgentTaskFailureRepository repository;

    public AgentFailureRecorder(AgentTaskFailureRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String teamId, String title, String detail, boolean injected) {
        try {
            repository.save(new AgentTaskFailure(
                    teamId,
                    truncate(title, MAX_TITLE_LENGTH),
                    truncate(detail, MAX_DETAIL_LENGTH),
                    injected,
                    Instant.now()
            ));
        } catch (Exception exception) {
            log.warn("failed to record agent task failure | teamId={} title={} error={}",
                    teamId, title, exception.getMessage());
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1) + "…";
    }
}
