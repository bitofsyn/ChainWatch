package com.chainwatch.backend.sanctions.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.chainwatch.backend.sanctions.client.OfacListClient;
import com.chainwatch.backend.sanctions.domain.SanctionedAddress;
import com.chainwatch.backend.sanctions.exception.SanctionsSyncException;
import com.chainwatch.backend.sanctions.repository.SanctionedAddressRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 제재 주소 동기화 검증: 소스 리스트 파싱(주석·비주소 줄 무시, 소문자 정규화),
 * 신규 등재/제외(delist)/재등재(relist) diff 반영, 빈 리스트 시 동기화 중단(fail-safe).
 */
@SpringBootTest
class SanctionedAddressServiceTest {

    private static final String ADDRESS_A = "0x7f367cc41522ce07553e823bf3be79a889debe1b";
    private static final String ADDRESS_B = "0x901bb9583b24d97e995513c6778dc6888ab6870e";

    @Autowired
    private SanctionedAddressService service;

    @Autowired
    private SanctionedAddressRepository repository;

    @MockitoBean
    private OfacListClient listClient;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void syncParsesListAndActivatesAddresses() {
        when(listClient.fetchRaw()).thenReturn("""
                # comment line
                %s
                not-an-address
                %s
                """.formatted(ADDRESS_A.toUpperCase().replace("0X", "0x"), ADDRESS_B));

        SanctionedAddressService.SyncSummary summary = service.syncNow();

        assertThat(summary.added()).isEqualTo(2);
        assertThat(summary.delisted()).isZero();
        assertThat(summary.activeTotal()).isEqualTo(2);
        // 대소문자 무관 조회 (스냅샷은 소문자 정규화 저장)
        assertThat(service.isSanctioned(ADDRESS_A)).isTrue();
        assertThat(service.isSanctioned(ADDRESS_A.toUpperCase().replace("0X", "0x"))).isTrue();
        assertThat(service.isSanctioned("0x0000000000000000000000000000000000000001")).isFalse();
        assertThat(repository.countByActiveTrue()).isEqualTo(2);
    }

    @Test
    void syncDelistsRemovedAddressesAndRelistsReturningOnes() {
        when(listClient.fetchRaw()).thenReturn(ADDRESS_A + "\n" + ADDRESS_B);
        service.syncNow();

        // A가 리스트에서 빠지면 행 삭제 없이 active=false + delistedAt으로 남는다.
        when(listClient.fetchRaw()).thenReturn(ADDRESS_B);
        SanctionedAddressService.SyncSummary afterRemoval = service.syncNow();

        assertThat(afterRemoval.delisted()).isEqualTo(1);
        assertThat(service.isSanctioned(ADDRESS_A)).isFalse();
        SanctionedAddress delisted = repository.findAll().stream()
                .filter(row -> row.getAddress().equals(ADDRESS_A))
                .findFirst().orElseThrow();
        assertThat(delisted.isActive()).isFalse();
        assertThat(delisted.getDelistedAt()).isNotNull();

        // 다시 나타나면 재등재된다.
        when(listClient.fetchRaw()).thenReturn(ADDRESS_A + "\n" + ADDRESS_B);
        SanctionedAddressService.SyncSummary afterReturn = service.syncNow();

        assertThat(afterReturn.added()).isEqualTo(1);
        assertThat(service.isSanctioned(ADDRESS_A)).isTrue();
        assertThat(repository.countByActiveTrue()).isEqualTo(2);
    }

    @Test
    void emptyListAbortsSyncKeepingSnapshot() {
        when(listClient.fetchRaw()).thenReturn(ADDRESS_A);
        service.syncNow();

        when(listClient.fetchRaw()).thenReturn("# 주석뿐인 응답\n\n");

        assertThatThrownBy(service::syncNow).isInstanceOf(SanctionsSyncException.class);
        // 대량 제외 사고 방지: 기존 스냅샷과 DB가 그대로 유지된다.
        assertThat(service.isSanctioned(ADDRESS_A)).isTrue();
        assertThat(repository.countByActiveTrue()).isEqualTo(1);
    }
}
