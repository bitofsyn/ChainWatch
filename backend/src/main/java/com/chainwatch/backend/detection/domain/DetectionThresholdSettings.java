package com.chainwatch.backend.detection.domain;

import com.chainwatch.backend.detection.config.DetectionAddressLists;
import com.chainwatch.backend.detection.config.DetectionThresholds;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * 관리자 API로 변경된 탐지 threshold·주소 목록의 영속 사본. 단일 행(id=1)만 사용한다.
 * 행이 없으면 application.yml 값이 그대로 적용 중이라는 뜻이다.
 */
@Entity
@Table(name = "detection_threshold_settings")
public class DetectionThresholdSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal largeTransferThresholdEth;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal exchangeFlowThresholdEth;

    @Column(nullable = false)
    private int rapidTransferThresholdCount;

    @Column(nullable = false)
    private long rapidTransferWindowMinutes;

    @Column(nullable = false)
    private int fanOutThresholdRecipients;

    @Column(nullable = false)
    private long fanOutWindowMinutes;

    @Column(nullable = false)
    private long ruleCooldownMinutes;

    /** 쉼표 구분 저장(주소는 0x hex라 쉼표 불가). null이면 이 기능 도입 전 행 — yml 목록 적용. */
    @Column(length = 25000)
    private String watchlistAddresses;

    @Column(length = 25000)
    private String exchangeAddresses;

    @Column(nullable = false, length = 100)
    private String updatedBy;

    @Column(nullable = false)
    private Instant updatedAt;

    protected DetectionThresholdSettings() {
    }

    public DetectionThresholdSettings(
            DetectionThresholds thresholds,
            DetectionAddressLists addressLists,
            String updatedBy,
            Instant updatedAt
    ) {
        this.id = SINGLETON_ID;
        apply(thresholds, addressLists, updatedBy, updatedAt);
    }

    public void apply(
            DetectionThresholds thresholds,
            DetectionAddressLists addressLists,
            String updatedBy,
            Instant updatedAt
    ) {
        this.largeTransferThresholdEth = thresholds.largeTransferThresholdEth();
        this.exchangeFlowThresholdEth = thresholds.exchangeFlowThresholdEth();
        this.rapidTransferThresholdCount = thresholds.rapidTransferThresholdCount();
        this.rapidTransferWindowMinutes = thresholds.rapidTransferWindowMinutes();
        this.fanOutThresholdRecipients = thresholds.fanOutThresholdRecipients();
        this.fanOutWindowMinutes = thresholds.fanOutWindowMinutes();
        this.ruleCooldownMinutes = thresholds.ruleCooldownMinutes();
        this.watchlistAddresses = joinAddresses(addressLists.watchlistAddresses());
        this.exchangeAddresses = joinAddresses(addressLists.exchangeAddresses());
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public DetectionThresholds toThresholds() {
        return new DetectionThresholds(
                largeTransferThresholdEth,
                exchangeFlowThresholdEth,
                rapidTransferThresholdCount,
                rapidTransferWindowMinutes,
                fanOutThresholdRecipients,
                fanOutWindowMinutes,
                ruleCooldownMinutes
        );
    }

    /** null이면 이 기능 도입 전에 저장된 행 — 호출부가 yml 목록으로 폴백한다. */
    public DetectionAddressLists toAddressLists() {
        if (watchlistAddresses == null && exchangeAddresses == null) {
            return null;
        }
        return new DetectionAddressLists(splitAddresses(watchlistAddresses), splitAddresses(exchangeAddresses));
    }

    private static String joinAddresses(List<String> addresses) {
        return String.join(",", addresses);
    }

    private static List<String> splitAddresses(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joined.split(",")).filter(address -> !address.isBlank()).toList();
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
