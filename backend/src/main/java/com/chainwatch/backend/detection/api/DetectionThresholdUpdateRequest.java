package com.chainwatch.backend.detection.api;

import java.math.BigDecimal;
import java.util.List;

/** 탐지 정책 부분 수정 요청. null 필드는 현재 값을 유지한다(빈 리스트는 "목록 비우기"). */
public record DetectionThresholdUpdateRequest(
        BigDecimal largeTransferThresholdEth,
        BigDecimal exchangeFlowThresholdEth,
        Integer rapidTransferThresholdCount,
        Long rapidTransferWindowMinutes,
        Integer fanOutThresholdRecipients,
        Long fanOutWindowMinutes,
        Long ruleCooldownMinutes,
        List<String> watchlistAddresses,
        List<String> exchangeAddresses
) {
}
