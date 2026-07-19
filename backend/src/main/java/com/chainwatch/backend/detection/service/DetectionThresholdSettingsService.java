package com.chainwatch.backend.detection.service;

import com.chainwatch.backend.audit.service.AuditLogService;
import com.chainwatch.backend.detection.api.DetectionThresholdUpdateRequest;
import com.chainwatch.backend.detection.config.DetectionAddressLists;
import com.chainwatch.backend.detection.config.DetectionAddressListsProvider;
import com.chainwatch.backend.detection.config.DetectionProperties;
import com.chainwatch.backend.detection.config.DetectionThresholds;
import com.chainwatch.backend.detection.config.DetectionThresholdsProvider;
import com.chainwatch.backend.detection.domain.DetectionThresholdSettings;
import com.chainwatch.backend.detection.repository.DetectionThresholdSettingsRepository;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탐지 threshold·주소 목록의 런타임 관리자. application.yml 값을 기본으로 하되,
 * 관리자 API로 변경된 값은 DB(detection_threshold_settings 단일 행)에 영속되어
 * 재기동 후에도 유지되고 룰 평가에 즉시 반영된다. 변경은 감사 로그로 남는다.
 */
@Service
public class DetectionThresholdSettingsService implements DetectionThresholdsProvider, DetectionAddressListsProvider {

    private static final Logger log = LoggerFactory.getLogger(DetectionThresholdSettingsService.class);
    static final String AUDIT_ACTION = "DETECTION_THRESHOLD_UPDATE";
    private static final Pattern ETH_ADDRESS = Pattern.compile("^0x[0-9a-fA-F]{40}$");
    private static final int MAX_ADDRESSES = 500;

    private final DetectionProperties detectionProperties;
    private final DetectionThresholdSettingsRepository repository;
    private final AuditLogService auditLogService;
    private final AtomicReference<DetectionThresholds> snapshot = new AtomicReference<>();
    private final AtomicReference<DetectionAddressLists> addressSnapshot = new AtomicReference<>();
    private final AtomicReference<DetectionThresholdMeta> meta = new AtomicReference<>();

    /** 마지막 변경 이력(없으면 yml 기본값 적용 중). */
    public record DetectionThresholdMeta(String updatedBy, Instant updatedAt) {
    }

    public DetectionThresholdSettingsService(
            DetectionProperties detectionProperties,
            DetectionThresholdSettingsRepository repository,
            AuditLogService auditLogService
    ) {
        this.detectionProperties = detectionProperties;
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @PostConstruct
    void load() {
        repository.findById(DetectionThresholdSettings.SINGLETON_ID).ifPresentOrElse(
                settings -> {
                    snapshot.set(settings.toThresholds());
                    DetectionAddressLists stored = settings.toAddressLists();
                    addressSnapshot.set(stored != null
                            ? stored
                            : DetectionAddressLists.fromProperties(detectionProperties));
                    meta.set(new DetectionThresholdMeta(settings.getUpdatedBy(), settings.getUpdatedAt()));
                    log.info("Detection settings loaded from DB (updatedBy={}, updatedAt={})",
                            settings.getUpdatedBy(), settings.getUpdatedAt());
                },
                () -> {
                    snapshot.set(DetectionThresholds.fromProperties(detectionProperties));
                    addressSnapshot.set(DetectionAddressLists.fromProperties(detectionProperties));
                }
        );
    }

    @Override
    public DetectionThresholds current() {
        return snapshot.get();
    }

    @Override
    public DetectionAddressLists currentAddresses() {
        return addressSnapshot.get();
    }

    /** null이면 아직 관리자 변경 이력이 없다(= application.yml 값 적용 중). */
    public DetectionThresholdMeta lastUpdate() {
        return meta.get();
    }

    @Transactional
    public void update(DetectionThresholdUpdateRequest request) {
        DetectionThresholds current = snapshot.get();
        DetectionAddressLists currentAddresses = addressSnapshot.get();

        DetectionThresholds next = new DetectionThresholds(
                orCurrent(request.largeTransferThresholdEth(), current.largeTransferThresholdEth()),
                orCurrent(request.exchangeFlowThresholdEth(), current.exchangeFlowThresholdEth()),
                request.rapidTransferThresholdCount() != null
                        ? request.rapidTransferThresholdCount() : current.rapidTransferThresholdCount(),
                request.rapidTransferWindowMinutes() != null
                        ? request.rapidTransferWindowMinutes() : current.rapidTransferWindowMinutes(),
                request.fanOutThresholdRecipients() != null
                        ? request.fanOutThresholdRecipients() : current.fanOutThresholdRecipients(),
                request.fanOutWindowMinutes() != null
                        ? request.fanOutWindowMinutes() : current.fanOutWindowMinutes(),
                request.ruleCooldownMinutes() != null
                        ? request.ruleCooldownMinutes() : current.ruleCooldownMinutes()
        );
        DetectionAddressLists nextAddresses = new DetectionAddressLists(
                request.watchlistAddresses() != null
                        ? normalizeAddresses(request.watchlistAddresses(), "watchlist 주소")
                        : currentAddresses.watchlistAddresses(),
                request.exchangeAddresses() != null
                        ? normalizeAddresses(request.exchangeAddresses(), "거래소 주소")
                        : currentAddresses.exchangeAddresses()
        );
        validate(next);

        String actor = resolveActor();
        Instant now = Instant.now();
        DetectionThresholdSettings settings = repository.findById(DetectionThresholdSettings.SINGLETON_ID)
                .orElseGet(() -> new DetectionThresholdSettings(next, nextAddresses, actor, now));
        settings.apply(next, nextAddresses, actor, now);
        repository.save(settings);

        String changes = describeChanges(current, next, currentAddresses, nextAddresses);
        auditLogService.record(AUDIT_ACTION, "DETECTION_RULES", "thresholds", changes);

        snapshot.set(next);
        addressSnapshot.set(nextAddresses);
        meta.set(new DetectionThresholdMeta(actor, now));
        log.info("Detection settings updated by {}: {}", actor, changes);
    }

    /** 공백 제거·소문자 정규화·중복 제거 후 형식을 검증한다. */
    private static List<String> normalizeAddresses(List<String> addresses, String label) {
        if (addresses.size() > MAX_ADDRESSES) {
            throw new IllegalArgumentException(label + "은(는) 최대 " + MAX_ADDRESSES + "개까지 등록할 수 있습니다.");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String address : addresses) {
            String trimmed = address == null ? "" : address.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!ETH_ADDRESS.matcher(trimmed).matches()) {
                throw new IllegalArgumentException(
                        label + " 형식이 올바르지 않습니다: " + truncateForMessage(trimmed) + " (0x + 40자리 hex)");
            }
            normalized.add(trimmed.toLowerCase(Locale.ROOT));
        }
        return List.copyOf(normalized);
    }

    private static String truncateForMessage(String value) {
        return value.length() <= 60 ? value : value.substring(0, 60) + "…";
    }

    private static void validate(DetectionThresholds next) {
        requirePositive(next.largeTransferThresholdEth(), "대규모 이체 임계값(ETH)");
        requirePositive(next.exchangeFlowThresholdEth(), "거래소 플로우 임계값(ETH)");
        requireRange(next.rapidTransferThresholdCount(), 2, 100_000, "반복 이체 기준 횟수");
        requireRange(next.rapidTransferWindowMinutes(), 1, 1_440, "반복 이체 집계 창(분)");
        requireRange(next.fanOutThresholdRecipients(), 2, 100_000, "자금 분산 기준 수신자 수");
        requireRange(next.fanOutWindowMinutes(), 1, 1_440, "자금 분산 집계 창(분)");
        requireRange(next.ruleCooldownMinutes(), 0, 10_080, "룰 cooldown(분)");
    }

    private static void requirePositive(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(label + "은(는) 0보다 커야 합니다.");
        }
    }

    private static void requireRange(long value, long min, long max, String label) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + "은(는) " + min + "~" + max + " 범위여야 합니다.");
        }
    }

    private static BigDecimal orCurrent(BigDecimal requested, BigDecimal current) {
        return requested != null ? requested : current;
    }

    private static String resolveActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "anonymous";
    }

    /** 감사 로그용 변경 요약: 바뀐 필드만 "이름 old→new"로, 주소 목록은 추가/삭제 건수로 나열한다. */
    private static String describeChanges(
            DetectionThresholds before,
            DetectionThresholds after,
            DetectionAddressLists beforeAddresses,
            DetectionAddressLists afterAddresses
    ) {
        StringJoiner joiner = new StringJoiner(", ");
        appendChange(joiner, "largeTransferThresholdEth",
                before.largeTransferThresholdEth(), after.largeTransferThresholdEth());
        appendChange(joiner, "exchangeFlowThresholdEth",
                before.exchangeFlowThresholdEth(), after.exchangeFlowThresholdEth());
        appendChange(joiner, "rapidTransferThresholdCount",
                before.rapidTransferThresholdCount(), after.rapidTransferThresholdCount());
        appendChange(joiner, "rapidTransferWindowMinutes",
                before.rapidTransferWindowMinutes(), after.rapidTransferWindowMinutes());
        appendChange(joiner, "fanOutThresholdRecipients",
                before.fanOutThresholdRecipients(), after.fanOutThresholdRecipients());
        appendChange(joiner, "fanOutWindowMinutes",
                before.fanOutWindowMinutes(), after.fanOutWindowMinutes());
        appendChange(joiner, "ruleCooldownMinutes",
                before.ruleCooldownMinutes(), after.ruleCooldownMinutes());
        appendAddressChange(joiner, "watchlistAddresses",
                beforeAddresses.watchlistAddresses(), afterAddresses.watchlistAddresses());
        appendAddressChange(joiner, "exchangeAddresses",
                beforeAddresses.exchangeAddresses(), afterAddresses.exchangeAddresses());
        return joiner.length() > 0 ? joiner.toString() : "변경 없음";
    }

    private static void appendChange(StringJoiner joiner, String name, Object before, Object after) {
        boolean changed = before instanceof BigDecimal beforeDecimal && after instanceof BigDecimal afterDecimal
                ? beforeDecimal.compareTo(afterDecimal) != 0
                : !before.equals(after);
        if (changed) {
            joiner.add(name + " " + before + "→" + after);
        }
    }

    private static void appendAddressChange(StringJoiner joiner, String name, List<String> before, List<String> after) {
        if (before.equals(after)) {
            return;
        }
        long added = after.stream().filter(address -> !before.contains(address)).count();
        long removed = before.stream().filter(address -> !after.contains(address)).count();
        joiner.add(name + " " + before.size() + "→" + after.size() + "개 (+" + added + "/-" + removed + ")");
    }
}
