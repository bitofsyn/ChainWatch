package com.chainwatch.backend.detection.api;

import com.chainwatch.backend.detection.config.DetectionProperties;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rule Engine에 등록된 탐지 규칙과 현재 적용 중인 threshold를 노출한다.
 * "왜 탐지되었는가"를 사용자가 확인할 수 있게 하는 설명용 API.
 */
@RestController
@RequestMapping("/api/detection/rules")
public class DetectionRuleController {

    private final DetectionProperties detectionProperties;

    public DetectionRuleController(DetectionProperties detectionProperties) {
        this.detectionProperties = detectionProperties;
    }

    @GetMapping
    public DetectionRulesResponse getRules() {
        int watchlistSize = detectionProperties.watchlistAddresses() != null
                ? detectionProperties.watchlistAddresses().size() : 0;
        int exchangeAddressSize = detectionProperties.exchangeAddresses() != null
                ? detectionProperties.exchangeAddresses().size() : 0;

        List<DetectionRulesResponse.Rule> rules = List.of(
                new DetectionRulesResponse.Rule(
                        "LARGE_TRANSFER",
                        "대규모 이체 탐지",
                        "단일 트랜잭션 금액이 임계값 이상이면 탐지합니다.",
                        detectionProperties.largeTransferThresholdEth() + " ETH 이상",
                        "HIGH",
                        85,
                        true
                ),
                new DetectionRulesResponse.Rule(
                        "EXCHANGE_FLOW",
                        "거래소 입출금 탐지",
                        "등록된 거래소 주소로의 대규모 입금(위험 88점) 또는 출금(78점)을 탐지합니다.",
                        detectionProperties.exchangeFlowThresholdEth() + " ETH 이상, 거래소 주소 "
                                + exchangeAddressSize + "개 등록",
                        "HIGH",
                        88,
                        exchangeAddressSize > 0
                ),
                new DetectionRulesResponse.Rule(
                        "RAPID_TRANSFER",
                        "반복 이체 탐지",
                        "동일 지갑이 짧은 시간 창 안에서 임계 횟수 이상 이체하면 탐지합니다.",
                        detectionProperties.rapidTransferWindowMinutes() + "분 내 "
                                + detectionProperties.rapidTransferThresholdCount() + "회 이상",
                        "MEDIUM",
                        72,
                        true
                ),
                new DetectionRulesResponse.Rule(
                        "WHALE_ACTIVITY",
                        "관심 지갑(고래) 활동 탐지",
                        "watchlist에 등록된 지갑이 송신 또는 수신에 관여하면 탐지합니다.",
                        "watchlist 주소 " + watchlistSize + "개 등록",
                        "HIGH",
                        90,
                        watchlistSize > 0
                ),
                new DetectionRulesResponse.Rule(
                        "FAN_OUT",
                        "자금 분산(fan-out) 그래프 탐지",
                        "한 지갑이 짧은 시간 창 안에 임계값 이상의 서로 다른 주소로 송금하는 "
                                + "그래프 out-degree 패턴(peeling chain/스플리팅)을 탐지합니다.",
                        detectionProperties.fanOutWindowMinutes() + "분 내 서로 다른 수신자 "
                                + detectionProperties.fanOutThresholdRecipients() + "개 이상",
                        "HIGH",
                        78,
                        detectionProperties.fanOutThresholdRecipients() > 1
                )
        );

        return new DetectionRulesResponse(String.valueOf(detectionProperties.mode()), rules);
    }
}
