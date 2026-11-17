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
     * <p>지갑의 행위 패턴을 보는 룰(RAPID_TRANSFER/FAN_OUT)과 관심 주소 룰(WHALE_ACTIVITY)이
     * 대상이다 — 바쁜 지갑은 후속 트랜잭션마다 다시 발화해 트랜잭션당 1건씩 이벤트가 폭증하기 때문이다.
     * 트랜잭션 단건이 그 자체로 의미 있는 룰(LARGE_TRANSFER 등)은 대상이 아니다.
     */
    default EventType cooldownEventType() {
        return null;
    }

    /**
     * cooldown을 판단할 지갑. 룰이 이벤트의 walletAddress로 쓰는 주소와 같아야 한다.
     * 기본은 발신 주소이고, null을 돌려주면 이 트랜잭션에는 cooldown을 적용하지 않는다.
     */
    default String cooldownWalletAddress(Transaction transaction) {
        return transaction.getFromAddress();
    }
}
