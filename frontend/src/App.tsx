import { startTransition, useCallback, useEffect, useState } from "react";
import {
  fetchEvents,
  fetchHealth,
  fetchRecentEventFeed,
  fetchRecentTransactionFeed
} from "./api";
import type {
  DetectionEventItem,
  FeedEventItem,
  FeedTransactionItem,
  HealthResponse
} from "./types";
import { CRITICAL_THRESHOLD, formatDate, formatEventType, shortenAddress, toStatus } from "./lib/format";
import { aggregateEventTypeCounts, type EventFilters } from "./lib/events";
import { SearchFilterBar } from "./components/SearchFilterBar";
import { DistributionChart } from "./components/DistributionChart";
import { useTheme } from "./hooks/useTheme";

const EVENT_PAGE_SIZE = 20;

export default function App() {
  const { theme, toggleTheme } = useTheme();
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [events, setEvents] = useState<DetectionEventItem[]>([]);
  const [eventFeed, setEventFeed] = useState<FeedEventItem[]>([]);
  const [transactionFeed, setTransactionFeed] = useState<FeedTransactionItem[]>([]);
  const [filters, setFilters] = useState<EventFilters>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadEvents = useCallback(async (activeFilters: EventFilters) => {
    try {
      const eventsData = await fetchEvents(activeFilters, EVENT_PAGE_SIZE);
      startTransition(() => {
        setEvents(eventsData.content);
        setError(null);
      });
    } catch {
      startTransition(() => {
        setError("탐지 이벤트 조회에 실패했습니다. 백엔드 상태를 확인해주세요.");
      });
    }
  }, []);

  useEffect(() => {
    let active = true;

    async function loadDashboard() {
      try {
        const [healthData, eventsData, eventFeedData, transactionFeedData] = await Promise.all([
          fetchHealth(),
          fetchEvents({}, EVENT_PAGE_SIZE),
          fetchRecentEventFeed(),
          fetchRecentTransactionFeed()
        ]);

        if (!active) {
          return;
        }

        startTransition(() => {
          setHealth(healthData);
          setEvents(eventsData.content);
          setEventFeed(eventFeedData);
          setTransactionFeed(transactionFeedData);
          setError(null);
          setLoading(false);
        });
      } catch {
        if (!active) {
          return;
        }

        startTransition(() => {
          setError("백엔드 API에 연결되지 않았습니다. local 프로필 백엔드를 실행해주세요.");
          setLoading(false);
        });
      }
    }

    loadDashboard();
    return () => {
      active = false;
    };
  }, []);

  const criticalCount = events.filter((event) => event.riskScore >= CRITICAL_THRESHOLD).length;
  const typeDistribution = aggregateEventTypeCounts(events);

  return (
    <main className="app-shell">
      <section className="hero-panel">
        <div className="hero-copy">
          <div className="hero-top">
            <p className="eyebrow">ChainWatch 대시보드</p>
            <button
              type="button"
              className="ghost-button theme-toggle"
              onClick={toggleTheme}
              aria-label="테마 전환"
            >
              {theme === "dark" ? "☀️ 라이트" : "🌙 다크"}
            </button>
          </div>
          <h1>실시간 위험 대응을 위한 AI 기반 온체인 이상거래 모니터링</h1>
          <p className="hero-text">
            Kafka, Redis, PostgreSQL, AI 리포트 파이프라인을 기반으로 대규모 자금 이동,
            거래소 입출금 패턴, 고래 지갑 활동을 추적하는 백엔드 중심 탐지 시스템입니다.
          </p>
          {error ? <div className="banner error">{error}</div> : null}
        </div>

        <div className="hero-metrics">
          <article className="metric-card">
            <span>탐지 이벤트</span>
            <strong>{events.length}</strong>
            <small>현재 조회 결과</small>
          </article>
          <article className="metric-card">
            <span>치명 위험</span>
            <strong>{criticalCount}</strong>
            <small>위험 점수 {CRITICAL_THRESHOLD} 이상</small>
          </article>
          <article className="metric-card">
            <span>백엔드 상태</span>
            <strong>{health?.status ?? (loading ? "LOADING" : "DOWN")}</strong>
            <small>{health?.service ?? "chainwatch-backend 미연결"}</small>
          </article>
        </div>
      </section>

      <section className="grid-panel">
        <article className="glass-card wide">
          <div className="section-head">
            <div>
              <p className="section-kicker">탐지 피드</p>
              <h2>우선 대응이 필요한 이상거래 목록</h2>
            </div>
          </div>

          <SearchFilterBar
            filters={filters}
            onChange={setFilters}
            onSearch={() => loadEvents(filters)}
          />

          <div className="table-wrap">
            {events.length === 0 && !loading ? (
              <div className="empty-state">조건에 맞는 탐지 이벤트가 없습니다.</div>
            ) : null}

            {events.map((row) => (
              <div className="event-row" key={row.id}>
                <div>
                  <span className={`status-pill ${toStatus(row.riskScore)}`}>
                    {row.riskLevel.toLowerCase()}
                  </span>
                  <h3>{formatEventType(row.eventType)}</h3>
                </div>
                <p>{row.summary}</p>
                <div className="wallet-col">
                  <span title={row.walletAddress}>{shortenAddress(row.walletAddress)}</span>
                  <strong>{row.riskScore}</strong>
                </div>
              </div>
            ))}
          </div>
        </article>

        <article className="glass-card">
          <div className="section-head compact">
            <div>
              <p className="section-kicker">유형 분포</p>
              <h2>이벤트 유형별 탐지 현황</h2>
            </div>
          </div>

          <DistributionChart
            data={typeDistribution}
            emptyMessage="집계할 탐지 이벤트가 없습니다."
          />
        </article>

        <article className="glass-card">
          <div className="section-head compact">
            <div>
              <p className="section-kicker">시스템 상태</p>
              <h2>파이프라인 상태</h2>
            </div>
          </div>

          <ul className="pulse-list">
            <li>
              <span className={`dot ${health?.status === "UP" ? "online" : "standby"}`} />
              백엔드 헬스 체크: {health?.status ?? "확인 불가"}
            </li>
            <li>
              <span className={`dot ${transactionFeed.length > 0 ? "online" : "standby"}`} />
              Redis 최근 트랜잭션 피드: {transactionFeed.length}건
            </li>
            <li>
              <span className={`dot ${eventFeed.length > 0 ? "online" : "standby"}`} />
              Redis 최근 이벤트 피드: {eventFeed.length}건
            </li>
          </ul>
        </article>

        <article className="glass-card">
          <div className="section-head compact">
            <div>
              <p className="section-kicker">실시간 피드</p>
              <h2>최근 수집/탐지 내역</h2>
            </div>
          </div>

          <div className="notes-feed">
            {transactionFeed.length === 0 && eventFeed.length === 0 && !loading ? (
              <div className="empty-state">최근 피드 데이터가 없습니다.</div>
            ) : null}

            {transactionFeed.map((item) => (
              <div className="note-item" key={`tx-${item.txHash}`}>
                블록 {item.blockNumber}에서 트랜잭션 {item.txHash.slice(0, 12)}... 수집
                <small>{formatDate(item.timestamp)}</small>
              </div>
            ))}

            {eventFeed.map((item) => (
              <div className="note-item" key={`event-${item.eventId}`}>
                {formatEventType(item.eventType)} 이벤트 감지, 위험 점수 {item.riskScore}
                <small>{formatDate(item.detectedAt)}</small>
              </div>
            ))}
          </div>
        </article>
      </section>
    </main>
  );
}
