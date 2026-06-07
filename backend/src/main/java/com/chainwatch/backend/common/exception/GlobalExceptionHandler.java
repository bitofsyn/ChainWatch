package com.chainwatch.backend.common.exception;

import com.chainwatch.backend.collector.exception.CollectorException;
import com.chainwatch.backend.analysis.exception.AiAnalysisException;
import com.chainwatch.backend.feed.exception.FeedCacheException;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CollectorException.class)
    public ResponseEntity<ApiErrorResponse> handleCollectorException(CollectorException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiErrorResponse.of("COLLECTOR_ERROR", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of("BAD_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiErrorResponse> handleIoException(IOException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiErrorResponse.of("RPC_IO_ERROR", exception.getMessage()));
    }

    @ExceptionHandler(FeedCacheException.class)
    public ResponseEntity<ApiErrorResponse> handleFeedCacheException(FeedCacheException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("FEED_CACHE_ERROR", exception.getMessage()));
    }

    @ExceptionHandler(AiAnalysisException.class)
    public ResponseEntity<ApiErrorResponse> handleAiAnalysisException(AiAnalysisException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiErrorResponse.of("AI_ANALYSIS_ERROR", exception.getMessage()));
    }
}
