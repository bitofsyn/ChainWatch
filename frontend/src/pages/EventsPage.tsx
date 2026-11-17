import { startTransition, useCallback, useEffect, useState } from "react";
import { ApiError, fetchEvents, updateEventStatusInBulk } from "../api";
import type { DetectionEventItem, EventLifecycleStatus } from "../types";
import { useAuth } from "../contexts/AuthContext";
import {
  formatDate,
  formatEventType,
  formatLifecycleStatus,
  LIFECYCLE_STATUS_ORDER,
  shortenAddress
} from "../lib/format";
import { formatNumber } from "../lib/opsOverview";
import type { EventFilters } from "../lib/events";
import { SearchFilterBar } from "../components/SearchFilterBar";
import { Pagination } from "../components/Pagination";
import { RiskBadge } from "../components/RiskBadge";
import { StatusBadge } from "../components/StatusBadge";
import { ChainBadge } from "../components/ChainBadge";
import { DataState } from "../components/DataState";

const PAGE_SIZE = 20;

type QueueScope = "all" | "mine" | "unassigned";

interface EventsPageProps {
  /** 딥링크(#/events?riskLevel=...)로 진입할 때의 초기 필터 */
  initialFilters?: EventFilters;
}

export function EventsPage({ initialFilters }: EventsPageProps = {}) {
  const { user, canOperate } = useAuth();
  // 일괄 처리: 선택한 이벤트에 같은 상태 변경을 한 번에 적용한다.
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [bulkStatus, setBulkStatus] = useState<EventLifecycleStatus>("ACKNOWLEDGED");
  const [bulkReason, setBulkReason] = useState("");
  const [bulkAssignToMe, setBulkAssignToMe] = useState(true);
  const [bulkSaving, setBulkSaving] = useState(false);
  const [bulkMessage, setBulkMessage] = useState<{ ok: boolean; text: string } | null>(null);
  const [events, setEvents] = useState<DetectionEventItem[]>([]);
  const [filters, setFilters] = useState<EventFilters>(initialFilters ?? {});
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

  const bulkNeedsReason = bulkStatus === "RESOLVED" || bulkStatus === "FALSE_POSITIVE";
  const allOnPageSelected = events.length > 0 && events.every((row) => selected.has(row.id));

  const toggleRow = (id: number) => {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const togglePage = () => {
    setSelected((current) => {
      const next = new Set(current);
      events.forEach((row) => (allOnPageSelected ? next.delete(row.id) : next.add(row.id)));
      return next;
    });
  };

  const applyBulk = async () => {
    if (selected.size === 0 || (bulkNeedsReason && !bulkReason.trim())) {
      return;
    }
    setBulkSaving(true);
    setBulkMessage(null);
    try {
      const result = await updateEventStatusInBulk({
        ids: [...selected],
        status: bulkStatus,
        assignee: bulkAssignToMe && user ? user.username : undefined,
        resolutionReason: bulkStatus === "RESOLVED" ? bulkReason.trim() : undefined,
        falsePositiveReason: bulkStatus === "FALSE_POSITIVE" ? bulkReason.trim() : undefined
      });
      setBulkMessage({
        ok: true,
        text: `${result.updated}건을 '${formatLifecycleStatus(bulkStatus)}'(으)로 변경했습니다.`
      });
      setSelected(new Set());
      setBulkReason("");
      await load(filters, page);
    } catch (cause) {
      setBulkMessage({
        ok: false,
        text: cause instanceof ApiError && cause.message ? cause.message : "일괄 처리에 실패했습니다."
      });
    } finally {
      setBulkSaving(false);
    }
  };

  const handleReset = () => {
    setFilters({});
    setPage(0);
    load({}, 0);
  };

  const activeScope: QueueScope = filters.unassigned
    ? "unassigned"
    : user && filters.assignee?.toLowerCase() === user.username.toLowerCase()
      ? "mine"
      : "all";

  // 담당자 스코프 퀵필터. 다른 필터(유형/등급/상태/기간)는 유지하고 담당자 축만 바꾼다.
  const applyScope = (scope: QueueScope) => {
    const next: EventFilters = { ...filters, assignee: undefined, unassigned: undefined };
    if (scope === "mine" && user) {
      next.assignee = user.username;
    } else if (scope === "unassigned") {
      next.unassigned = true;
    }
    setFilters(next);
    setPage(0);
    load(next, 0);
  };

  return (
    <>
      <section className="page-head">
        <div>
          <p className="eyebrow">DETECTION EVENTS</p>
          <h1>이상 거래 검토</h1>
          <p className="page-lede">
            규칙이 탐지한 이벤트를 위험도와 상태별로 확인하고, 상세 화면에서 탐지 근거와 관련 거래를 추적할 수 있습니다.
          </p>
        </div>
      </section>

      <section className="panel-card">
        <div className="queue-scope" role="group" aria-label="담당자 큐 선택">
          <button
            type="button"
            className={`ghost-button ${activeScope === "all" ? "selected" : ""}`}
            onClick={() => applyScope("all")}
          >
            전체 큐
          </button>
          {user && canOperate ? (
            <button
              type="button"
              className={`ghost-button ${activeScope === "mine" ? "selected" : ""}`}
              onClick={() => applyScope("mine")}
            >
              내 케이스
            </button>
          ) : null}
          {canOperate ? (
            <button
              type="button"
              className={`ghost-button ${activeScope === "unassigned" ? "selected" : ""}`}
              onClick={() => applyScope("unassigned")}
            >
              미할당
            </button>
          ) : null}
        </div>

        <SearchFilterBar filters={filters} onChange={setFilters} onSearch={handleSearch} onReset={handleReset} />

        <p className="result-count">
          총 <strong>{formatNumber(totalElements)}</strong>건
        </p>

        <DataState
          loading={loading && events.length === 0}
          error={error}
          onRetry={() => load(filters, page)}
          empty={!loading && !error && events.length === 0}
          emptyMessage="조건에 맞는 탐지 이벤트가 없습니다."
        />

        {canOperate && (selected.size > 0 || bulkMessage) ? (
          <div className="bulk-bar" role="region" aria-label="일괄 처리">
            <strong>{selected.size}건 선택</strong>
            <label>
              변경할 상태
              <select
                value={bulkStatus}
                onChange={(evt) => setBulkStatus(evt.target.value as EventLifecycleStatus)}
              >
                {LIFECYCLE_STATUS_ORDER.map((status) => (
                  <option key={status} value={status}>
                    {formatLifecycleStatus(status)}
                  </option>
                ))}
              </select>
            </label>
            {bulkNeedsReason ? (
              <label className="bulk-reason">
                사유 (필수)
                <input
                  type="text"
                  value={bulkReason}
                  onChange={(evt) => setBulkReason(evt.target.value)}
                  placeholder={bulkStatus === "RESOLVED" ? "해결 사유" : "오탐으로 본 근거"}
                  maxLength={500}
                />
              </label>
            ) : null}
            <label className="bulk-check">
              <input
                type="checkbox"
                checked={bulkAssignToMe}
                onChange={(evt) => setBulkAssignToMe(evt.target.checked)}
              />
              나에게 할당
            </label>
            <button
              type="button"
              className="primary-button"
              onClick={applyBulk}
              disabled={bulkSaving || selected.size === 0 || (bulkNeedsReason && !bulkReason.trim())}
            >
              {bulkSaving ? "적용 중…" : "선택 항목에 적용"}
            </button>
            <button type="button" className="ghost-button" onClick={() => setSelected(new Set())}>
              선택 해제
            </button>
            {bulkMessage ? (
              <span className={`banner ${bulkMessage.ok ? "success" : "error"}`} role="status">
                {bulkMessage.text}
              </span>
            ) : null}
          </div>
        ) : null}

        {events.length > 0 ? (
          <div className="table-scroll">
            <table className="data-table event-queue" aria-label="탐지 이벤트 작업 큐">
              <thead>
                <tr>
                  {canOperate ? (
                    <th scope="col">
                      <input
                        type="checkbox"
                        aria-label="이 페이지 전체 선택"
                        checked={allOnPageSelected}
                        onChange={togglePage}
                      />
                    </th>
                  ) : null}
                  <th scope="col">#</th>
                  <th scope="col">위험</th>
                  <th scope="col" className="num">
                    점수
                  </th>
                  <th scope="col">상태</th>
                  <th scope="col">체인</th>
                  <th scope="col">유형</th>
                  <th scope="col">요약</th>
                  <th scope="col">지갑</th>
                  <th scope="col">담당자</th>
                  <th scope="col">탐지 시각</th>
                </tr>
              </thead>
              <tbody>
                {events.map((row) => (
                  <tr key={row.id} className={selected.has(row.id) ? "row-selected" : undefined}>
                    {canOperate ? (
                      <td>
                        <input
                          type="checkbox"
                          aria-label={`이벤트 ${row.id} 선택`}
                          checked={selected.has(row.id)}
                          onChange={() => toggleRow(row.id)}
                        />
                      </td>
                    ) : null}
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
                      <ChainBadge network={row.network} />
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
