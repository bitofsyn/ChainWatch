package com.chainwatch.backend.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "password_history", indexes = {
        @Index(name = "idx_password_history_user_ended", columnList = "user_id,ended_at DESC")
})
public class PasswordHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    @Column(name = "used_at", nullable = false)
    private Instant usedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected PasswordHistory() {}

    public PasswordHistory(Long userId, String passwordHash, Instant usedAt) {
        this.userId = userId;
        this.passwordHash = passwordHash;
        this.usedAt = usedAt;
    }

    public PasswordHistory(Long userId, String passwordHash, Instant usedAt, Instant endedAt) {
        this(userId, passwordHash, usedAt);
        this.endedAt = endedAt;
    }

    public String getPasswordHash() { return passwordHash; }
    public Instant getEndedAt() { return endedAt; }
}
