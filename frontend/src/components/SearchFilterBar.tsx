import { useState } from "react";
import {
  EVENT_TYPE_LABELS,
  LIFECYCLE_STATUS_LABELS,
  LIFECYCLE_STATUS_ORDER,
  NETWORK_LABELS,
  NETWORK_ORDER,
  RISK_LEVEL_LABELS
} from "../lib/format";
import type { EventFilters } from "../lib/events";

interface SearchFilterBarProps {
  filters: EventFilters;
  onChange: (filters: EventFilters) => void;
  onSearch: () => void;
  onReset: () => void;
}

export function SearchFilterBar({ filters, onChange, onSearch, onReset }: SearchFilterBarProps) {
  const advancedCount = [filters.network, filters.assignee, filters.from, filters.to].filter(Boolean).length;
  const hasFilters = Object.values(filters).some((value) => value !== undefined && value !== "" && value !== false);
  const [showAdvanced, setShowAdvanced] = useState(advancedCount > 0 || Boolean(filters.unassigned));

  const reset = () => {
    onReset();
    setShowAdvanced(false);
  };

  return (
    <form className="event-filter-panel" onSubmit={(event) => { event.preventDefault(); onSearch(); }}>
      <div className="event-filter-primary">
        <label className="filter-field">
          <span>탐지 유형</span>
          <select value={filters.eventType ?? ""} onChange={(event) => onChange({ ...filters, eventType: event.target.value || undefined })}>
            <option value="">전체 유형</option>
            {Object.entries(EVENT_TYPE_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
        </label>

        <label className="filter-field">
          <span>위험도</span>
          <select value={filters.riskLevel ?? ""} onChange={(event) => onChange({ ...filters, riskLevel: event.target.value || undefined })}>
            <option value="">전체 등급</option>
            {Object.entries(RISK_LEVEL_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
        </label>

        <label className="filter-field">
          <span>처리 상태</span>
          <select value={filters.status ?? ""} onChange={(event) => onChange({ ...filters, status: event.target.value || undefined })}>
            <option value="">전체 상태</option>
            {LIFECYCLE_STATUS_ORDER.map((value) => <option key={value} value={value}>{LIFECYCLE_STATUS_LABELS[value]}</option>)}
          </select>
        </label>

        <label className="filter-field filter-wallet">
          <span>지갑 주소</span>
          <input type="search" placeholder="0x로 시작하는 주소" value={filters.wallet ?? ""} onChange={(event) => onChange({ ...filters, wallet: event.target.value || undefined })} />
        </label>

        <button type="submit" className="primary-button filter-submit">검색</button>
      </div>

      <div className="event-filter-toolbar">
        <button type="button" className="filter-detail-toggle" aria-expanded={showAdvanced} onClick={() => setShowAdvanced((current) => !current)}>
          상세 필터 {advancedCount > 0 ? <strong>{advancedCount}</strong> : null}
          <span aria-hidden="true">{showAdvanced ? "▴" : "▾"}</span>
        </button>
        {hasFilters ? <button type="button" className="filter-reset" onClick={reset}>필터 초기화</button> : null}
      </div>

      {showAdvanced ? (
        <div className="event-filter-advanced">
          <label className="filter-field">
            <span>네트워크</span>
            <select value={filters.network ?? ""} onChange={(event) => onChange({ ...filters, network: event.target.value || undefined })}>
              <option value="">전체 네트워크</option>
              {NETWORK_ORDER.map((value) => <option key={value} value={value}>{NETWORK_LABELS[value]}</option>)}
            </select>
          </label>

          <label className="filter-field">
            <span>담당자</span>
            <input type="search" placeholder={filters.unassigned ? "미할당 필터 적용 중" : "사용자 아이디"} disabled={filters.unassigned} value={filters.assignee ?? ""} onChange={(event) => onChange({ ...filters, assignee: event.target.value || undefined })} />
          </label>

          <label className="filter-field">
            <span>조회 시작</span>
            <input type="datetime-local" value={filters.from ?? ""} max={filters.to ?? undefined} onChange={(event) => onChange({ ...filters, from: event.target.value || undefined })} />
          </label>

          <label className="filter-field">
            <span>조회 종료</span>
            <input type="datetime-local" value={filters.to ?? ""} min={filters.from ?? undefined} onChange={(event) => onChange({ ...filters, to: event.target.value || undefined })} />
          </label>
        </div>
      ) : null}
    </form>
  );
}
