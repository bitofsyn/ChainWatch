package com.chainwatch.backend.detection.rule;

import com.chainwatch.backend.detection.domain.DetectionCommand;
import com.chainwatch.backend.event.domain.EventType;
import com.chainwatch.backend.transaction.domain.Transaction;
import java.util.Optional;

public interface DetectionRule {
    Optional<DetectionCommand> evaluate(Transaction transaction);

    /**
     * 지갑 단위 cooldown을 적용할 이벤트 유형. null이면 cooldown을 적용하지 않는다.
     *
     * <p>발신 지갑의 행위 패턴을 보는 룰(RAPID_TRANSFER/FAN_OUT)만 대상으로 한다 — 바쁜 지갑은
     * 창 안의 모든 후속 트랜잭션이 다시 발화해 트랜잭션당 1건씩 이벤트가 폭증하기 때문이다.
     * cooldown 대상 룰은 walletAddress로 트랜잭션의 fromAddress를 사용한다는 계약을 전제한다.
     * 트랜잭션 단건이 그 자체로 의미 있는 룰(LARGE_TRANSFER 등)은 대상이 아니다.
     */
    default EventType cooldownEventType() {
        return null;
    }
}
