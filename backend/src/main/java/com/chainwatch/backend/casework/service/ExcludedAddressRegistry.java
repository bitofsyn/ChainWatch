package com.chainwatch.backend.casework.service;

import com.chainwatch.backend.casework.domain.PolicySuggestion;
import com.chainwatch.backend.casework.repository.PolicySuggestionRepository;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 관리자가 승인한 탐지 예외 주소 목록. 탐지는 트랜잭션마다 호출되므로 메모리에 캐시하고
 * 승인·해제 시점에만 다시 읽는다.
 */
@Component
public class ExcludedAddressRegistry {

    private final PolicySuggestionRepository repository;
    private volatile Set<String> addresses;

    public ExcludedAddressRegistry(PolicySuggestionRepository repository) {
        this.repository = repository;
    }

    public boolean isExcluded(String address) {
        if (address == null) {
            return false;
        }
        Set<String> current = addresses;
        if (current == null) {
            current = reload();
        }
        return current.contains(address.toLowerCase(Locale.ROOT));
    }

    public Set<String> reload() {
        Set<String> loaded = repository.findByStatus(PolicySuggestion.STATUS_ACCEPTED).stream()
                .map(suggestion -> suggestion.getAddress().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        addresses = loaded;
        return loaded;
    }
}
