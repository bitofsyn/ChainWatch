package com.chainwatch.backend.user.repository;

import com.chainwatch.backend.user.domain.UserAccount;
import com.chainwatch.backend.user.domain.UserRole;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUsername(String username);

    boolean existsByUsername(String username);

    /** 마지막 활성 ADMIN 강등/비활성화 가드에 사용한다. */
    long countByRoleAndActiveTrue(UserRole role);
}
