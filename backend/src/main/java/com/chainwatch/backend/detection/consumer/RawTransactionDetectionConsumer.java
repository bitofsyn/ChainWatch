package com.chainwatch.backend.detection.consumer;

import com.chainwatch.backend.collector.kafka.RawTransactionEvent;
import com.chainwatch.backend.collector.util.GasFees;
import com.chainwatch.backend.detection.service.DetectionService;
import com.chainwatch.backend.transaction.domain.Transaction;
import com.chainwatch.backend.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * raw-transactions 토픽을 구독해 탐지를 수행하는 Consumer.
 * Detection Server 분리 시 이 클래스가 그대로 분리 서버의 진입점이 된다.
 *
 * <p>멱등성: 트랜잭션은 txHash로 dedupe하고, 탐지 이벤트는
 * (transactionId, eventType) 중복 검사를 DetectionService가 수행하므로 재전달에 안전하다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.kafka", name = "bootstrap-servers")
@ConditionalOnProperty(prefix = "chainwatch.detection", name = "mode", havingValue = "kafka")
public class RawTransactionDetectionConsumer {

    private static final Logger log = LoggerFactory.getLogger(RawTransactionDetectionConsumer.class);
    private static final String CONTRACT_CREATION_ADDRESS = "CONTRACT_CREATION";

    private final TransactionRepository transactionRepository;
    private final DetectionService detectionService;

    public RawTransactionDetectionConsumer(
            TransactionRepository transactionRepository,
            DetectionService detectionService
    ) {
        this.transactionRepository = transactionRepository;
        this.detectionService = detectionService;
    }

    @KafkaListener(
            topics = "${chainwatch.kafka.topics.raw-transactions}",
            groupId = "chainwatch-detection",
            containerFactory = "rawTransactionKafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(RawTransactionEvent event) {
        Transaction transaction = transactionRepository.findByTxHash(event.txHash())
                .orElseGet(() -> transactionRepository.save(toTransaction(event)));
        detectionService.analyzeTransaction(transaction);
        log.debug("Analyzed raw transaction {} from block {}", event.txHash(), event.blockNumber());
    }

    /**
     * Collector와 저장소를 공유하지 않는 배포(분리된 Detection Server)나
     * 이벤트가 저장보다 먼저 도착한 경우를 위한 폴백 저장.
     */
    private Transaction toTransaction(RawTransactionEvent event) {
        return new Transaction(
                event.txHash(),
                event.fromAddress(),
                event.toAddress() == null ? CONTRACT_CREATION_ADDRESS : event.toAddress(),
                event.valueEth(),
                GasFees.estimateFeeEth(event.gasPriceWei(), event.maxFeePerGasWei(), event.gas()),
                event.blockNumber(),
                event.timestamp(),
                event.contractAddress(),
                event.network()
        );
    }
}
