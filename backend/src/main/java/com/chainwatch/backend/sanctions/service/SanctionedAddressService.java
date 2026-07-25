package com.chainwatch.backend.sanctions.service;

import com.chainwatch.backend.audit.service.AuditLogService;
import com.chainwatch.backend.sanctions.client.OfacListClient;
import com.chainwatch.backend.sanctions.config.SanctionsProperties;
import com.chainwatch.backend.sanctions.domain.SanctionedAddress;
import com.chainwatch.backend.sanctions.exception.SanctionsSyncException;
import com.chainwatch.backend.sanctions.repository.SanctionedAddressRepository;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

/**
 * 제재 주소 목록의 런타임 관리자. 기동 시 DB의 활성 주소로 스냅샷을 만들고,
 * 동기화(주기 잡 또는 관리자 수동 트리거)가 소스 리스트와 DB를 diff해
 * 신규 등재/재등재/제외를 반영한 뒤 스냅샷을 교체한다 — 룰 평가에 재기동 없이 즉시 반영된다.
 * 동기화 실패 시에는 기존 스냅샷을 유지한다(fail-safe).
 */
@Service
public class SanctionedAddressService implements SanctionedAddressChecker {

    private static final Logger log = LoggerFactory.getLogger(SanctionedAddressService.class);

    static final String AUDIT_ACTION = "SANCTIONS_SYNC";
    public static final String SOURCE_OFAC_SDN = "OFAC_SDN";
    private static final String SYSTEM_ACTOR = "system";
    private static final Pattern ETH_ADDRESS = Pattern.compile("^0x[0-9a-fA-F]{40}$");

    /** 마지막 동기화 결과 (신규 등재/제외/활성 총계) */
    public record SyncSummary(int added, int delisted, int activeTotal, Instant syncedAt) {
    }

    private final OfacListClient listClient;
    private final SanctionedAddressRepository repository;
    private final SanctionsProperties properties;
    private final AuditLogService auditLogService;
    private final AtomicReference<Set<String>> activeSnapshot = new AtomicReference<>(Set.of());
    private final AtomicReference<SyncSummary> lastSync = new AtomicReference<>();

    public SanctionedAddressService(
            OfacListClient listClient,
            SanctionedAddressRepository repository,
            SanctionsProperties properties,
            AuditLogService auditLogService
    ) {
        this.listClient = listClient;
        this.repository = repository;
        this.properties = properties;
        this.auditLogService = auditLogService;
    }

    @PostConstruct
    void load() {
        Set<String> active = repository.findAllByActiveTrue().stream()
                .map(SanctionedAddress::getAddress)
                .collect(Collectors.toUnmodifiableSet());
        activeSnapshot.set(active);
        if (!active.isEmpty()) {
            log.info("Sanctioned address snapshot loaded from DB: {} active address(es)", active.size());
        }
    }

    @Override
    public boolean isSanctioned(String address) {
        return address != null && activeSnapshot.get().contains(address.toLowerCase(Locale.ROOT));
    }

    public int activeCount() {
        return activeSnapshot.get().size();
    }

    /** null이면 이 프로세스 기동 후 동기화 이력이 없다(DB 스냅샷만 사용 중). */
    public SyncSummary lastSyncSummary() {
        return lastSync.get();
    }

    /**
     * 소스 리스트를 내려받아 DB와 diff한다: 신규 주소는 등재, 사라진 주소는 제외(active=false),
     * 과거 제외됐다 다시 나타난 주소는 재등재. 리스트가 비어 있으면 대량 제외 사고를 막기 위해
     * 동기화를 중단한다.
     */
    @Transactional
    public SyncSummary syncNow() {
        String raw;
        try {
            raw = listClient.fetchRaw();
        } catch (RestClientException exception) {
            throw new SanctionsSyncException(
                    "제재 주소 리스트 다운로드 실패 (source=" + properties.sourceUrl() + ")", exception);
        }
        Set<String> fetched = parseAddresses(raw);
        if (fetched.isEmpty()) {
            throw new SanctionsSyncException(
                    "제재 주소 리스트가 비어 있어 동기화를 중단합니다 (source=" + properties.sourceUrl() + ")");
        }

        Map<String, SanctionedAddress> existing = repository.findAll().stream()
                .collect(Collectors.toMap(SanctionedAddress::getAddress, Function.identity()));
        Instant now = Instant.now();
        int added = 0;
        int delisted = 0;
        for (String address : fetched) {
            SanctionedAddress current = existing.get(address);
            if (current == null) {
                repository.save(new SanctionedAddress(address, SOURCE_OFAC_SDN, now));
                added++;
            } else if (!current.isActive()) {
                current.relist();
                added++;
            }
        }
        for (SanctionedAddress current : existing.values()) {
            if (current.isActive() && !fetched.contains(current.getAddress())) {
                current.delist(now);
                delisted++;
            }
        }

        activeSnapshot.set(Set.copyOf(fetched));
        SyncSummary summary = new SyncSummary(added, delisted, fetched.size(), now);
        lastSync.set(summary);
        if (added > 0 || delisted > 0) {
            auditLogService.record(SYSTEM_ACTOR, null, AUDIT_ACTION, "SANCTIONS", "ofac-sdn",
                    "제재 주소 동기화: +" + added + "/-" + delisted + ", 활성 " + fetched.size()
                            + "건, source=" + properties.sourceUrl());
        }
        log.info("[SANCTIONS_SYNC] added={}, delisted={}, activeTotal={}", added, delisted, fetched.size());
        return summary;
    }

    /** 줄 단위 파싱: 공백·주석(#) 줄은 무시하고 0x+40hex 형식만 소문자 정규화해 채택한다. */
    private static Set<String> parseAddresses(String raw) {
        if (raw == null) {
            return Set.of();
        }
        Set<String> parsed = new LinkedHashSet<>();
        int skipped = 0;
        for (String line : raw.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (ETH_ADDRESS.matcher(trimmed).matches()) {
                parsed.add(trimmed.toLowerCase(Locale.ROOT));
            } else {
                skipped++;
            }
        }
        if (skipped > 0) {
            log.warn("[SANCTIONS_SYNC] skipped {} non-address line(s) from source list", skipped);
        }
        return parsed;
    }
}
