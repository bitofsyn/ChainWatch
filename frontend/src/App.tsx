import { startTransition, useEffect, useState } from "react";
import {
  fetchEvents,
  fetchHealth,
  fetchRecentEventFeed,
  fetchRecentTransactionFeed
} from "./api";
import type {
  DetectionEventItem,
  EventStatus,
  FeedEventItem,
  FeedTransactionItem,
  HealthResponse
} from "./types";

function toStatus(riskScore: number): EventStatus {
  if (riskScore >= 85) {
    return "critical";
  }
  if (riskScore >= 70) {
    return "high";
  }
  return "elevated";
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}

function formatEventType(value: string) {
  const map: Record<string, string> = {
    LARGE_TRANSFER: "대규모 이체",
    EXCHANGE_FLOW: "거래소 입출금",
    RAPID_TRANSFER: "반복 이체",
    WATCHLIST_ACTIVITY: "관심 지갑 활동"
  };

  return map[value] ?? value;
}

export default function App() {
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [events, setEvents] = useState<DetectionEventItem[]>([]);
  const [eventFeed, setEventFeed] = useState<FeedEventItem[]>([]);
  const [transactionFeed, setTransactionFeed] = useState<FeedTransactionItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function loadDashboard() {
      try {
        const [healthData, eventsData, eventFeedData, transactionFeedData] = await Promise.all([
          fetchHealth(),
          fetchEvents(),
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

  const criticalCount = events.filter((event) => event.riskScore >= 85).length;

  return (
    <main className="app-shell">
      <section className="hero-panel">
        <div className="hero-copy">
          <p className="eyebrow">ChainWatch 대시보드</p>
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
            <small>위험 점수 85 이상</small>
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
            <button className="ghost-button">최근 이벤트</button>
          </div>

          <div className="table-wrap">
            {events.length === 0 && !loading ? (
              <div className="empty-state">표시할 탐지 이벤트가 없습니다.</div>
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
                  <span>{row.walletAddress}</span>
                  <strong>{row.riskScore}</strong>
                </div>
              </div>
            ))}
          </div>
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
              <p className="section-kicker">최근 구현 메모</p>
              <h2>실시간 수집 피드</h2>
            </div>
          </div>

          <div className="notes-feed">
            {transactionFeed.length === 0 && eventFeed.length === 0 && !loading ? (
              <div className="empty-state">최근 피드 데이터가 없습니다.</div>
            ) : null}

            {transactionFeed.map((item) => (
              <div className="note-item" key={`tx-${item.transactionId}`}>
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
