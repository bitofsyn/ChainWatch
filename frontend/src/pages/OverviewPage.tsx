import { startTransition, useEffect, useState } from "react";
import {
  fetchEvents,
  fetchEventStats,
  fetchHealth,
  fetchRecentEventFeed,
  fetchRecentTransactionFeed
} from "../api";
import type {
  DetectionEventItem,
  EventStats,
  FeedEventItem,
  FeedTransactionItem,
  HealthResponse
} from "../types";
import {
  formatDate,
  formatEventType,
  RISK_LEVEL_LABELS,
  shortenAddress
} from "../lib/format";
import { DistributionChart } from "../components/DistributionChart";
import { RiskBadge } from "../components/RiskBadge";
import { StatusBadge } from "../components/StatusBadge";

const RISK_ORDER = ["CRITICAL", "HIGH", "MEDIUM", "LOW"];

function countOf(stats: EventStats | null, group: "riskLevelCounts" | "statusCounts", key: string): number {
  return stats?.[group].find((item) => item.key === key)?.count ?? 0;
}

export function OverviewPage() {
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [stats, setStats] = useState<EventStats | null>(null);
  const [priorityEvents, setPriorityEvents] = useState<DetectionEventItem[]>([]);
  const [eventFeed, setEventFeed] = useState<FeedEventItem[]>([]);
  const [transactionFeed, setTransactionFeed] = useState<FeedTransactionItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function load() {
      try {
        const [healthData, statsData, criticalPage, highPage, eventFeedData, transactionFeedData] =
          await Promise.all([
            fetchHealth(),
            fetchEventStats(),
            fetchEvents({ riskLevel: "CRITICAL" }, 5),
            fetchEvents({ riskLevel: "HIGH" }, 5),
            fetchRecentEventFeed(6),
            fetchRecentTransactionFeed(6)
          ]);

        if (!active) {
          return;
        }

        const queue = [...criticalPage.content, ...highPage.content]
          .sort((a, b) => b.riskScore - a.riskScore || b.detectedAt.localeCompare(a.detectedAt))
          .slice(0, 6);

        startTransition(() => {
          setHealth(healthData);
          setStats(statsData);
          setPriorityEvents(queue);
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
          setError("백엔드 API에 연결되지 않았습니다. 파이프라인 상태를 확인해주세요.");
          setLoading(false);
        });
      }
    }

    load();
    return () => {
      active = false;
    };
  }, []);

  const riskDistribution = RISK_ORDER.map((key) => ({
    key,
    label: RISK_LEVEL_LABELS[key] ?? key,
    count: countOf(stats, "riskLevelCounts", key)
  })).filter((item) => item.count > 0);

  const typeDistribution = (stats?.eventTypeCounts ?? [])
    .map((item) => ({ key: item.key, label: formatEventType(item.key), count: item.count }))
    .sort((a, b) => b.count - a.count);

  const unresolvedCount = countOf(stats, "statusCounts", "NEW");

  return (
    <>
      <section className="page-head">
        <div>
          <p className="eyebrow">관제 현황</p>
          <h1>온체인 이상거래 관제</h1>
          <p className="page-lede">
            수집 파이프라인이 탐지한 이상거래를 위험도와 처리 상태 기준으로 요약합니다.
            우선순위 큐에서 대응이 필요한 이벤트부터 확인하세요.
          </p>
        </div>
        {error ? <div className="banner error">{error}</div> : null}
      </section>

      <section className="kpi-grid">
        <article className="metric-card">
          <span>누적 탐지</span>
          <strong>{loading ? "-" : stats?.totalEvents ?? 0}</strong>
          <small>24시간 신규 {loading ? "-" : stats?.last24hEvents ?? 0}건</small>
        </article>
        <article className="metric-card">
          <span>치명 위험</span>
          <strong className="text-critical">
            {loading ? "-" : countOf(stats, "riskLevelCounts", "CRITICAL")}
          </strong>
          <small>CRITICAL 등급</small>
        </article>
        <article className="metric-card">
          <span>높은 위험</span>
          <strong className="text-high">
            {loading ? "-" : countOf(stats, "riskLevelCounts", "HIGH")}
          </strong>
          <small>HIGH 등급</small>
        </article>
        <article className="metric-card">
          <span>미처리 이벤트</span>
          <strong className={unresolvedCount > 0 ? "text-high" : ""}>
            {loading ? "-" : unresolvedCount}
          </strong>
          <small>NEW 상태 (대응 대기)</small>
        </article>
        <article className="metric-card">
          <span>백엔드 상태</span>
          <strong>{health?.status ?? (loading ? "…" : "DOWN")}</strong>
          <small>{health?.service ?? "chainwatch-backend 미연결"}</small>
        </article>
      </section>

      <section className="grid-panel">
        <article className="glass-card wide">
          <div className="section-head">
            <div>
              <p className="section-kicker">우선순위 큐</p>
              <h2>대응이 필요한 고위험 이벤트</h2>
            </div>
            <a className="ghost-button" href="#/events">
              전체 보기
            </a>
          </div>

          <div className="table-wrap">
            {priorityEvents.length === 0 && !loading ? (
              <div className="empty-state">CRITICAL/HIGH 등급의 미처리 이벤트가 없습니다.</div>
            ) : null}

            {priorityEvents.map((row) => (
              <a className="event-row linked" key={row.id} href={`#/events/${row.id}`}>
                <div>
                  <div className="badge-group">
                    <RiskBadge riskLevel={row.riskLevel} riskScore={row.riskScore} />
                    <StatusBadge status={row.status} />
                  </div>
                  <h3>{formatEventType(row.eventType)}</h3>
                </div>
                <p>{row.summary}</p>
                <div className="wallet-col">
                  <span title={row.walletAddress}>{shortenAddress(row.walletAddress)}</span>
                  <strong>{row.riskScore}</strong>
                </div>
              </a>
            ))}
          </div>
        </article>

        <article className="glass-card">
          <div className="section-head compact">
            <div>
              <p className="section-kicker">위험도 분포</p>
              <h2>등급별 탐지 현황</h2>
            </div>
          </div>
          <DistributionChart data={riskDistribution} emptyMessage="집계할 탐지 이벤트가 없습니다." />
        </article>

        <article className="glass-card">
          <div className="section-head compact">
            <div>
              <p className="section-kicker">유형 분포</p>
              <h2>이벤트 유형별 탐지 현황</h2>
            </div>
          </div>
          <DistributionChart data={typeDistribution} emptyMessage="집계할 탐지 이벤트가 없습니다." />
        </article>

        <article className="glass-card">
          <div className="section-head compact">
            <div>
              <p className="section-kicker">반복 탐지</p>
              <h2>탐지 다발 지갑 상위 {stats?.topWallets.length ?? 0}개</h2>
            </div>
          </div>
          <div className="notes-feed">
            {(stats?.topWallets ?? []).length === 0 && !loading ? (
              <div className="empty-state">반복 탐지된 지갑이 없습니다.</div>
            ) : null}
            {(stats?.topWallets ?? []).map((wallet) => (
              <a
                className="note-item linked"
                key={wallet.walletAddress}
                href={`#/wallets/${encodeURIComponent(wallet.walletAddress)}`}
              >
                <span className="mono">{shortenAddress(wallet.walletAddress, 10, 8)}</span> —{" "}
                {wallet.eventCount}건 탐지, 최고 위험 점수 {wallet.maxRiskScore}
                {wallet.lastDetectedAt ? <small>최근 {formatDate(wallet.lastDetectedAt)}</small> : null}
              </a>
            ))}
          </div>
        </article>

        <article className="glass-card wide-span">
          <div className="section-head compact">
            <div>
              <p className="section-kicker">실시간 피드</p>
              <h2>최근 수집/탐지 내역</h2>
            </div>
          </div>
          <div className="feed-columns">
            <div className="notes-feed">
              {eventFeed.length === 0 && !loading ? (
                <div className="empty-state">최근 탐지 이벤트가 없습니다.</div>
              ) : null}
              {eventFeed.map((item) => (
                <a className="note-item linked" key={`event-${item.eventId}`} href={`#/events/${item.eventId}`}>
                  {formatEventType(item.eventType)} 감지, 위험 점수 {item.riskScore}
                  <small>{formatDate(item.detectedAt)}</small>
                </a>
              ))}
            </div>
            <div className="notes-feed">
              {transactionFeed.length === 0 && !loading ? (
                <div className="empty-state">최근 수집된 트랜잭션이 없습니다.</div>
              ) : null}
              {transactionFeed.map((item) => (
                <div className="note-item" key={`tx-${item.txHash}`}>
                  블록 {item.blockNumber} · <span className="mono">{item.txHash.slice(0, 14)}...</span>
                  <small>{formatDate(item.timestamp)}</small>
                </div>
              ))}
            </div>
          </div>
        </article>
      </section>
    </>
  );
}
