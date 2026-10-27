package com.chainwatch.backend.user.repository;

import com.chainwatch.backend.user.domain.PasswordGlobalStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordGlobalStatRepository extends JpaRepository<PasswordGlobalStat, String> {
    @Modifying
    @Query("update PasswordGlobalStat s set s.useCount = s.useCount + 1 where s.fingerprint = :fingerprint and s.useCount < 100")
    int incrementIfBelowLimit(@Param("fingerprint") String fingerprint);

    @Modifying
    @Query("update PasswordGlobalStat s set s.useCount = s.useCount + 1 where s.fingerprint = :fingerprint")
    int incrementLegacy(@Param("fingerprint") String fingerprint);

    @Modifying
    @Query("update PasswordGlobalStat s set s.useCount = s.useCount - 1 where s.fingerprint = :fingerprint and s.useCount > 0")
    int decrement(@Param("fingerprint") String fingerprint);
}
