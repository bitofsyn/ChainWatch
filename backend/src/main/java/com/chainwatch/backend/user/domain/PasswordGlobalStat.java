package com.chainwatch.backend.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "password_global_stats")
public class PasswordGlobalStat {
    @Id
    @Column(length = 64)
    private String fingerprint;

    @Column(name = "use_count", nullable = false)
    private long useCount;

    protected PasswordGlobalStat() {}

    public String getFingerprint() { return fingerprint; }
    public long getUseCount() { return useCount; }
}
