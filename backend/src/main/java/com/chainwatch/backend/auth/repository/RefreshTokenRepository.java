package com.chainwatch.backend.auth.repository;

import com.chainwatch.backend.auth.domain.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 비활성화/비밀번호 변경 시 해당 사용자의 모든 세션을 즉시 무효화한다.
     * bulk update는 영속성 컨텍스트를 우회하므로 flush/clear로 스테일 엔티티를 막는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revoked = true where t.user.id = :userId and t.revoked = false")
    int revokeAllByUserId(@Param("userId") Long userId);

    /** 만료·폐기된 지 오래된 행 정리(스케줄러). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff or (t.revoked = true and t.issuedAt < :cutoff)")
    int deleteStaleTokens(@Param("cutoff") Instant cutoff);
}
