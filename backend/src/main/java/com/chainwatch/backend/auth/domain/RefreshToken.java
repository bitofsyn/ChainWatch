package com.chainwatch.backend.auth.domain;

import com.chainwatch.backend.user.domain.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 회전식 리프레시 토큰. 원문 토큰은 클라이언트에게 1회 전달되며 DB에는 SHA-256 해시만 저장한다.
 * refresh 시 새 토큰을 발급하고 기존 토큰은 revoked 처리 후 replaced_by_token_hash로 연결한다.
 * 이미 회전된(revoked + replaced) 토큰이 다시 제시되면 탈취 신호로 보고 사용자 전체 세션을 폐기한다.
 */
@Entity
@Table(
        name = "refresh_tokens",
        indexes = {
                @Index(name = "idx_refresh_tokens_token_hash", columnList = "token_hash", unique = true),
                @Index(name = "idx_refresh_tokens_user_id", columnList = "user_id"),
                @Index(name = "idx_refresh_tokens_expires_at", columnList = "expires_at")
        }
)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "replaced_by_token_hash", length = 64)
    private String replacedByTokenHash;

    protected RefreshToken() {
    }

    public RefreshToken(String tokenHash, UserAccount user, Instant issuedAt, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.user = user;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.revoked = false;
    }

    public void revoke() {
        this.revoked = true;
    }

    public void markReplacedBy(String newTokenHash) {
        this.revoked = true;
        this.replacedByTokenHash = newTokenHash;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    /** 이미 회전이 끝난 토큰의 재사용 여부(탈취 신호). */
    public boolean isReused() {
        return revoked && replacedByTokenHash != null;
    }

    public Long getId() {
        return id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public UserAccount getUser() {
        return user;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public String getReplacedByTokenHash() {
        return replacedByTokenHash;
    }
}
