package com.chainwatch.backend.casework.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 이벤트 처리 이력 한 줄. 상태 변경·담당자 변경·코멘트를 시간순으로 남겨
 * 분석가가 이벤트 화면에서 "누가 언제 무엇을 했는지" 볼 수 있게 한다.
 * 감사 로그(관리자 전용, 시스템 전반)와 달리 이벤트 단위의 업무 기록이다.
 */
@Entity
@Table(
        name = "event_activities",
        indexes = @Index(name = "idx_event_activities_event_id", columnList = "event_id")
)
public class EventActivity {

    public static final String TYPE_STATUS = "STATUS";
    public static final String TYPE_ASSIGNMENT = "ASSIGNMENT";
    public static final String TYPE_COMMENT = "COMMENT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(nullable = false, length = 100)
    private String actor;

    /** enum CHECK 제약 문제를 피하려고 문자열로 저장한다(TYPE_* 상수). */
    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 2000)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EventActivity() {
    }

    public EventActivity(Long eventId, String actor, String type, String detail) {
        this.eventId = eventId;
        this.actor = actor;
        this.type = type;
        this.detail = detail.length() > 2000 ? detail.substring(0, 2000) : detail;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getActor() {
        return actor;
    }

    public String getType() {
        return type;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
