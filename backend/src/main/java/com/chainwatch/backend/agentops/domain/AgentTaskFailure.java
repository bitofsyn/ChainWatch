package com.chainwatch.backend.agentops.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Agent 팀 작업 실패 이력. 실패 저장소가 없던 팀(detection/triage/ops)의 실패를 기록해
 * 콘솔 성공률·실패 타임라인의 근거 데이터로 사용한다.
 * injected=true는 장애 주입(드릴)으로 생성된 실패로, 주입 해제 시 정리 대상이다.
 */
@Entity
@Table(
        name = "agent_task_failures",
        indexes = {
                @Index(name = "idx_agent_task_failures_team_occurred", columnList = "team_id, occurred_at")
        }
)
public class AgentTaskFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false, length = 30)
    private String teamId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String detail;

    @Column(nullable = false)
    private Boolean injected;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AgentTaskFailure() {
    }

    public AgentTaskFailure(String teamId, String title, String detail, boolean injected, Instant occurredAt) {
        this.teamId = teamId;
        this.title = title;
        this.detail = detail;
        this.injected = injected;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public String getTeamId() {
        return teamId;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    public Boolean getInjected() {
        return injected;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
