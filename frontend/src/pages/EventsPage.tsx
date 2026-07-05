import { startTransition, useCallback, useEffect, useState } from "react";
import { fetchEvents } from "../api";
import type { DetectionEventItem } from "../types";
import { formatDate, formatEventType, shortenAddress } from "../lib/format";
import type { EventFilters } from "../lib/events";
import { SearchFilterBar } from "../components/SearchFilterBar";
import { Pagination } from "../components/Pagination";
import { RiskBadge } from "../components/RiskBadge";
import { StatusBadge } from "../components/StatusBadge";

const PAGE_SIZE = 20;

export function EventsPage() {
  const [events, setEvents] = useState<DetectionEventItem[]>([]);
  const [filters, setFilters] = useState<EventFilters>({});
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (activeFilters: EventFilters, activePage: number) => {
    setLoading(true);
    try {
      const data = await fetchEvents(activeFilters, PAGE_SIZE, activePage);
      startTransition(() => {
        setEvents(data.content);
        setTotalPages(data.totalPages);
        setTotalElements(data.totalElements);
        setError(null);
        setLoading(false);
      });
    } catch {
      startTransition(() => {
        setError("탐지 이벤트 조회에 실패했습니다. 백엔드 상태를 확인해주세요.");
        setLoading(false);
      });
    }
  }, []);

  useEffect(() => {
    load(filters, page);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  const handleSearch = () => {
    setPage(0);
    load(filters, 0);
  };

  return (
    <>
      <section className="page-head">
        <div>
          <p className="eyebrow">이상거래 목록</p>
          <h1>탐지 이벤트 전체 조회</h1>
          <p className="page-lede">
            유형·위험 등급·지갑 주소로 필터링할 수 있습니다. 행을 선택하면 상세 분석
            페이지로 이동합니다.
          </p>
        </div>
        {error ? <div className="banner error">{error}</div> : null}
      </section>

      <section className="glass-card">
        <SearchFilterBar filters={filters} onChange={setFilters} onSearch={handleSearch} />

        <p className="result-count">
          총 <strong>{totalElements}</strong>건
        </p>

        <div className="table-wrap">
          {events.length === 0 && !loading ? (
            <div className="empty-state">조건에 맞는 탐지 이벤트가 없습니다.</div>
          ) : null}

          {events.map((row) => (
            <a className="event-row linked detailed" key={row.id} href={`#/events/${row.id}`}>
              <div>
                <div className="badge-group">
                  <RiskBadge riskLevel={row.riskLevel} riskScore={row.riskScore} />
                  <StatusBadge status={row.status} />
                </div>
                <h3>{formatEventType(row.eventType)}</h3>
                <small className="event-time">{formatDate(row.detectedAt)}</small>
              </div>
              <p>{row.summary}</p>
              <div className="wallet-col">
                <span title={row.walletAddress}>{shortenAddress(row.walletAddress)}</span>
                <strong>{row.riskScore}</strong>
              </div>
            </a>
          ))}
        </div>

        <Pagination page={page} totalPages={totalPages} onChange={setPage} />
      </section>
    </>
  );
}
