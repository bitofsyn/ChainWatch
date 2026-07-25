package com.chainwatch.backend.sanctions.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * 제재 대상 암호화폐 주소. 리스트에서 제외되어도 행을 삭제하지 않고 active=false + delistedAt으로
 * 남긴다 — 과거 이벤트의 근거와 제재 이력 감사 추적을 보존하기 위해서다.
 */
@Entity
@Table(
        name = "sanctioned_addresses",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sanctioned_addresses_address",
                columnNames = "address"
        ),
        indexes = @Index(name = "idx_sanctioned_addresses_active", columnList = "active")
)
public class SanctionedAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소문자 정규화 저장 */
    @Column(nullable = false, length = 100)
    private String address;

    /** 출처 리스트 식별자 (예: OFAC_SDN) */
    @Column(nullable = false, length = 50)
    private String source;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Instant firstListedAt;

    /** 리스트에서 제외된 시각. active=true면 null. */
    private Instant delistedAt;

    protected SanctionedAddress() {
    }

    public SanctionedAddress(String address, String source, Instant firstListedAt) {
        this.address = address;
        this.source = source;
        this.active = true;
        this.firstListedAt = firstListedAt;
    }

    public Long getId() {
        return id;
    }

    public String getAddress() {
        return address;
    }

    public String getSource() {
        return source;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getFirstListedAt() {
        return firstListedAt;
    }

    public Instant getDelistedAt() {
        return delistedAt;
    }

    /** 리스트 재등재: 과거 delist 이력이 있는 주소가 다시 나타난 경우 */
    public void relist() {
        this.active = true;
        this.delistedAt = null;
    }

    public void delist(Instant delistedAt) {
        this.active = false;
        this.delistedAt = delistedAt;
    }
}
