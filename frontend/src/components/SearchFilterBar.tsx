import { EVENT_TYPE_LABELS, RISK_LEVEL_LABELS } from "../lib/format";
import type { EventFilters } from "../lib/events";

interface SearchFilterBarProps {
  filters: EventFilters;
  onChange: (filters: EventFilters) => void;
  onSearch: () => void;
}

export function SearchFilterBar({ filters, onChange, onSearch }: SearchFilterBarProps) {
  return (
    <form
      className="filter-bar"
      onSubmit={(event) => {
        event.preventDefault();
        onSearch();
      }}
    >
      <select
        aria-label="이벤트 유형"
        value={filters.eventType ?? ""}
        onChange={(event) => onChange({ ...filters, eventType: event.target.value || undefined })}
      >
        <option value="">전체 유형</option>
        {Object.entries(EVENT_TYPE_LABELS).map(([value, label]) => (
          <option key={value} value={value}>
            {label}
          </option>
        ))}
      </select>

      <select
        aria-label="위험 등급"
        value={filters.riskLevel ?? ""}
        onChange={(event) => onChange({ ...filters, riskLevel: event.target.value || undefined })}
      >
        <option value="">전체 등급</option>
        {Object.entries(RISK_LEVEL_LABELS).map(([value, label]) => (
          <option key={value} value={value}>
            {label}
          </option>
        ))}
      </select>

      <input
        type="search"
        aria-label="지갑 주소 검색"
        placeholder="지갑 주소 검색 (0x...)"
        value={filters.wallet ?? ""}
        onChange={(event) => onChange({ ...filters, wallet: event.target.value || undefined })}
      />

      <button type="submit" className="ghost-button">
        검색
      </button>
    </form>
  );
}
