package com.chainwatch.backend.sanctions.repository;

import com.chainwatch.backend.sanctions.domain.SanctionedAddress;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SanctionedAddressRepository extends JpaRepository<SanctionedAddress, Long> {

    List<SanctionedAddress> findAllByActiveTrue();

    long countByActiveTrue();
}
