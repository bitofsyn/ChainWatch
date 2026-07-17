package com.chainwatch.backend.analysis.client;

/** provider/model은 AI 서버가 실제 사용한 값(응답 필드). null이면 호출 측 설정값으로 폴백한다. */
public record AiAnalysisResult(
        String report,
        String rawResponse,
        String structuredJson,
        String provider,
        String model
) {
}
