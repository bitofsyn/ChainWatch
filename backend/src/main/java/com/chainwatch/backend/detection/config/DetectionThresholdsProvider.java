package com.chainwatch.backend.detection.config;

/**
 * 현재 적용 중인 탐지 threshold를 제공한다.
 * 룰은 이 인터페이스로만 threshold를 읽어, 관리자 API로 변경된 값이 재기동 없이 즉시 반영된다.
 */
public interface DetectionThresholdsProvider {

    DetectionThresholds current();
}
