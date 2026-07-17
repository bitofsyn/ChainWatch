import { startTransition, useEffect, useState } from "react";
import { fetchEvents, fetchTransactions, fetchWalletSummary } from "../api";
import type { DetectionEventItem, TransactionItem, WalletSummary } from "../types";
import { formatDate, formatEventType, RISK_LEVEL_LABELS, shortenAddress } from "../lib/format";
import { DistributionChart } from "../components/DistributionChart";
import { RiskBadge } from "../components/RiskBadge";
import { StatusBadge } from "../components/StatusBadge";

interface WalletDetailPageProps {
  address: string;
}

export function WalletDetailPage({ address }: WalletDetailPageProps) {
  const [summary, setSummary] = useState<WalletSummary | null>(null);
  const [events, setEvents] = useState<DetectionEventItem[]>([]);
  const [transactions, setTransactions] = useState<TransactionItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function load() {
      setLoading(true);
      try {
        const [summaryData, eventsData, transactionsData] = await Promise.all([
          fetchWalletSummary(address),
          fetchEvents({ wallet: address }, 10),
          fetchTransactions(address, 10)
        ]);

        if (!active) {
          return;
        }
        startTransition(() => {
          setSummary(summaryData);
          setEvents(eventsData.content);
          setTransactions(transactionsData.content);
          setError(null);
          setLoading(false);
        });
      } catch {
        if (!active) {
          return;
        }
        startTransition(() => {
          setError("지갑 정보 조회에 실패했습니다. 백엔드 상태를 확인해주세요.");
          setLoading(false);
        });
      }
    }

    load();
    return () => {
      active = false;
    };
  }, [address]);

  const typeDistribution = (summary?.eventTypeCounts ?? [])
    .map((item) => ({ key: item.key, label: formatEventType(item.key), count: item.count }))
    .sort((a, b) => b.count - a.count);

  const riskDistribution = (summary?.riskLevelCounts ?? [])
    .map((item) => ({ key: item.key, label: RISK_LEVEL_LABELS[item.key] ?? item.key, count: item.count }))
    .sort((a, b) => b.count - a.count);

  return (
    <>
      <section className="page-head">
        <div>
          <a className="back-link" href="#/events">
            ← 이상거래 목록
          </a>
          <p className="eyebrow">지갑 상세</p>
          <h1 className="mono heading-address">{shortenAddress(address, 14, 10)}</h1>
          <p className="page-lede mono">{address}</p>
        </div>
        {error ? <div className="banner error">{error}</div> : null}
      </section>

      <section className="kpi-grid">
        <article className="metric-card">
          <span>탐지 이력</span>
          <strong>{loading ? "-" : summary?.eventCount ?? 0}</strong>
          <small>이 지갑 관련 이벤트</small>
        </article>
        <article className="metric-card">
          <span>최고 위험 점수</span>
          <strong className={summary && summary.maxRiskScore >= 85 ? "text-critical" : ""}>
            {loading ? "-" : summary?.maxRiskScore ?? 0}
          </strong>
          <small>역대 최고</small>
        </article>
        <article className="metric-card">
          <span>최초 탐지</span>
          <strong className="metric-date">
            {summary?.firstDetectedAt ? formatDate(summary.firstDetectedAt) : "-"}
          </strong>
          <small>첫 이벤트</small>
        </article>
        <article className="metric-card">
          <span>최근 탐지</span>
          <strong className="metric-date">
            {summary?.lastDetectedAt ? formatDate(summary.lastDetectedAt) : "-"}
          </strong>
          <small>마지막 이벤트</small>
        </article>
      </section>

      <section className="grid-panel">
        <article className="glass-card wide">
          <div className="section-head">
            <div>
              <p className="section-kicker">탐지 이력</p>
              <h2>이 지갑의 이상거래 이벤트</h2>
            </div>
          </div>
          <div className="table-wrap">
            {events.length === 0 && !loading ? (
              <div className="empty-state">이 지갑에 대한 탐지 이력이 없습니다.</div>
            ) : null}
            {events.map((row) => (
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
                  <span>{formatDate(row.detectedAt)}</span>
                  <strong>{row.riskScore}</strong>
                </div>
              </a>
            ))}
          </div>
        </article>

        <article className="glass-card">
          <div className="section-head compact">
            <div>
              <p className="section-kicker">유형 분포</p>
              <h2>탐지 유형</h2>
            </div>
          </div>
          <DistributionChart data={typeDistribution} emptyMessage="탐지 이력이 없습니다." ariaLabel="지갑 이벤트 유형 분포 차트" />
        </article>

        <article className="glass-card">
          <div className="section-head compact">
            <div>
              <p className="section-kicker">위험도 분포</p>
              <h2>등급별 이력</h2>
            </div>
          </div>
          <DistributionChart data={riskDistribution} emptyMessage="탐지 이력이 없습니다." ariaLabel="지갑 위험도 분포 차트" />
        </article>

        <article className="glass-card wide-span">
          <div className="section-head compact">
            <div>
              <p className="section-kicker">온체인 기록</p>
              <h2>이 지갑의 수집된 트랜잭션</h2>
            </div>
          </div>
          <div className="table-wrap">
            {transactions.length === 0 && !loading ? (
              <div className="empty-state">수집된 트랜잭션이 없습니다.</div>
            ) : null}
            {transactions.map((tx) => (
              <a className="tx-row linked" key={tx.id} href={`#/transactions/${tx.id}`}>
                <span className="mono">{shortenAddress(tx.txHash, 12, 8)}</span>
                <span>
                  {shortenAddress(tx.fromAddress, 6, 4)} → {tx.toAddress ? shortenAddress(tx.toAddress, 6, 4) : "-"}
                </span>
                <span>{tx.amount} ETH</span>
                <span>블록 {tx.blockNumber}</span>
                <small>{formatDate(tx.timestamp)}</small>
              </a>
            ))}
          </div>
        </article>
      </section>
    </>
  );
}
