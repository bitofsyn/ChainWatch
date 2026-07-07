package com.chainwatch.backend.collector.service;

import com.chainwatch.backend.collector.config.CollectorProperties;
import com.chainwatch.backend.collector.domain.CollectorState;
import com.chainwatch.backend.collector.repository.CollectorStateRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 확정(finality) 판정: 마지막으로 관측한 체인 head와 설정된 confirmation depth로
 * 블록/트랜잭션이 "확정" 상태인지 계산한다.
 *
 * <p>저장 시점에 플래그를 박제하는 대신 head - blockNumber로 매번 계산하므로
 * 추가 마이그레이션·백필 없이 동작하고, reorg rewind로 재수집돼도 값이 어긋나지 않는다.
 * confirmationDepth(기본 12) > reorgRewindDepth(기본 6)이므로 "확정" 데이터는
 * rewind가 되감는 범위 밖에 있다.
 */
@Service
public class ChainFinalityService {

    private final CollectorStateRepository collectorStateRepository;
    private final CollectorProperties collectorProperties;

    public ChainFinalityService(
            CollectorStateRepository collectorStateRepository,
            CollectorProperties collectorProperties
    ) {
        this.collectorStateRepository = collectorStateRepository;
        this.collectorProperties = collectorProperties;
    }

    /** 마지막으로 관측한 체인 head. 수집이 한 번도 돌지 않았으면 empty. */
    public Optional<Long> lastKnownChainHead() {
        return collectorStateRepository.findById(CollectedBlockProcessor.COLLECTOR_NAME)
                .map(CollectorState::getLastKnownChainHead);
    }

    /**
     * 주어진 블록 번호의 확정 상태를 계산한다.
     * head를 모르는 경우(수집 전, 레거시 상태 행) confirmations/confirmed 모두 null인 UNKNOWN을 반환한다.
     *
     * @param chainHead {@link #lastKnownChainHead()} 결과. 목록 조회 시 요소마다 재조회하지 않도록 밖에서 한 번만 읽어 전달한다.
     */
    public Confirmation confirmationFor(Long blockNumber, Long chainHead) {
        if (blockNumber == null || chainHead == null) {
            return Confirmation.UNKNOWN;
        }
        // head 관측치가 오래돼 블록이 head보다 앞서면 0 confirmations(미확정)으로 취급한다.
        long confirmations = Math.max(0, chainHead - blockNumber + 1);
        return new Confirmation(confirmations, confirmations >= collectorProperties.confirmationDepth());
    }

    public int confirmationDepth() {
        return collectorProperties.confirmationDepth();
    }

    /** confirmations/confirmed가 null이면 판정 불가(head 미관측). */
    public record Confirmation(Long confirmations, Boolean confirmed) {
        public static final Confirmation UNKNOWN = new Confirmation(null, null);
    }
}
