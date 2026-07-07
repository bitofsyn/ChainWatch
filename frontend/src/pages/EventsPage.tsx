import { startTransition, useCallback, useEffect, useState } from "react";
import { fetchEvents } from "../api";
import type { DetectionEventItem } from "../types";
import { formatDate, formatEventType, shortenAddress } from "../lib/format";
import type { EventFilters } from "../lib/events";
import { SearchFilterBar } from "../components/SearchFilterBar";
import { Pagination } from "../components/Pagination";
import { RiskBadge } from "../components/RiskBadge";
import { StatusBadge } from "../components/StatusBadge";
import { DataState } from "../components/DataState";

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
          <p className="eyebrow">이상거래 큐</p>
          <h1>탐지 이벤트 작업 큐</h1>
          <p className="page-lede">
            상태·유형·위험 등급·지갑 주소·기간으로 필터링해 처리할 이벤트를 선별하세요.
          </p>
        </div>
      </section>

      <section className="panel-card">
        <SearchFilterBar filters={filters} onChange={setFilters} onSearch={handleSearch} />

        <p className="result-count">
          총 <strong>{totalElements}</strong>건
        </p>

        <DataState
          loading={loading && events.length === 0}
          error={error}
          onRetry={() => load(filters, page)}
          empty={!loading && !error && events.length === 0}
          emptyMessage="조건에 맞는 탐지 이벤트가 없습니다."
        />

        {events.length > 0 ? (
          <div className="table-scroll">
            <table className="data-table event-queue" aria-label="탐지 이벤트 작업 큐">
              <thead>
                <tr>
                  <th scope="col">#</th>
                  <th scope="col">위험</th>
                  <th scope="col" className="num">
                    점수
                  </th>
                  <th scope="col">상태</th>
                  <th scope="col">유형</th>
                  <th scope="col">요약</th>
                  <th scope="col">지갑</th>
                  <th scope="col">담당자</th>
                  <th scope="col">탐지 시각</th>
                </tr>
              </thead>
              <tbody>
                {events.map((row) => (
                  <tr key={row.id}>
                    <td className="num">
                      <a className="row-link" href={`#/events/${row.id}`}>
                        {row.id}
                      </a>
                    </td>
                    <td>
                      <RiskBadge riskLevel={row.riskLevel} riskScore={row.riskScore} />
                    </td>
                    <td className="num strong-num">{row.riskScore}</td>
                    <td>
                      <StatusBadge status={row.status} />
                    </td>
                    <td>
                      <a className="row-link" href={`#/events/${row.id}`}>
                        {formatEventType(row.eventType)}
                      </a>
                    </td>
                    <td className="cell-summary" title={row.summary}>
                      {row.summary}
                    </td>
                    <td className="mono" title={row.walletAddress}>
                      {shortenAddress(row.walletAddress)}
                    </td>
                    <td>{row.assignee || <span className="cell-muted">미지정</span>}</td>
                    <td className="cell-time">{formatDate(row.detectedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        <Pagination page={page} totalPages={totalPages} onChange={setPage} />
      </section>
    </>
  );
}
