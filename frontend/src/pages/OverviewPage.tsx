import { useState } from "react";
import { useOverviewData, RANGE_BUCKETS, type OpsRange } from "../hooks/useOverviewData";
import {
  ageFrom,
  backlogLevel,
  collectorLevel,
  formatAge,
  formatCompact,
  formatDelta,
  formatNumber,
  formatPercent,
  overallStatus,
  SURGE_MESSAGES,
  throughputInsight,
  BACKLOG_WARN,
  BACKLOG_DANGER,
  LAG_WARN_BLOCKS,
  LAG_DANGER_BLOCKS
} from "../lib/opsOverview";
import { formatDate, formatEventType, RISK_LEVEL_LABELS, shortenAddress } from "../lib/format";
import { MetricCard } from "../components/MetricCard";
import { TimeSeriesChart } from "../components/TimeSeriesChart";
import { PipelineHealthStrip } from "../components/PipelineHealthStrip";
import { RiskStatusMatrix } from "../components/RiskStatusMatrix";
import { DistributionChart } from "../components/DistributionChart";
import { DataState } from "../components/DataState";
import { RiskBadge } from "../components/RiskBadge";
import { StatusBadge } from "../components/StatusBadge";

const RANGE_OPTIONS: { value: OpsRange; label: string }[] = [
  { value: "1h", label: "1시간" },
  { value: "6h", label: "6시간" },
  { value: "24h", label: "24시간" }
];

const updatedFormat = new Intl.DateTimeFormat("ko-KR", {
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit"
});

export function OverviewPage() {
  const [range, setRange] = useState<OpsRange>("24h");
  const data = useOverviewData(range);
  const { overview, pipeline } = data;

  const status = overallStatus(
    pipeline?.components ?? null,
    overview?.collector ?? null,
    overview?.kpis ?? null
  );
  const insight = overview ? throughputInsight(overview.series) : null;

  const typeDistribution = (overview?.eventTypes ?? []).map((item) => ({
    key: item.key,
    label: formatEventType(item.key),
    count: item.count
  }));

  const collector = overview?.collector ?? null;
  const kpis = overview?.kpis ?? null;

  return (
    <>
      {/* A. Compact Operations Header */}
      <section className="ops-header">
        <div className="ops-header-title">
          <p className="eyebrow">관제 현황</p>
          <h1>실시간 리스크 관제</h1>
        </div>
        <div className="ops-header-status">
          <span className={`ops-overall level-${status.level}`}>
            전체 상태: <strong>{status.label}</strong>
          </span>
          <span className="ops-updated">
            {data.lastUpdated
              ? `마지막 갱신 ${updatedFormat.format(data.lastUpdated)}`
              : "갱신 이력 없음"}
            {data.refreshing ? <em className="ops-refreshing"> · 갱신 중</em> : null}
          </span>
          <span className="ops-auto-note">30초 자동 갱신</span>
          <button
            type="button"
            className="ghost-button"
            onClick={data.refresh}
            disabled={data.refreshing}
          >
            새로고침
          </button>
        </div>
        {data.allFailed ? (
          <div className="banner error" role="alert">
            백엔드 API에 연결되지 않았습니다. 파이프라인 상태를 확인해주세요.
          </div>
        ) : null}
      </section>

      {/* B. 4개 핵심 KPI */}
      <section className="ops-kpi-grid" aria-label="핵심 운영 지표">
        <MetricCard
          label="Collector Lag"
          value={collector?.lagBlocks != null ? `${formatNumber(collector.lagBlocks)} 블록` : "—"}
          sub={
            collector
              ? collector.lastCollectedBlock != null
                ? `마지막 수집 #${formatNumber(collector.lastCollectedBlock)} · 확정 ${collector.confirmationDepth} conf`
                : "수집 이력 없음"
              : data.overviewError
                ? "지표 조회 실패"
                : "불러오는 중"
          }
          level={collector ? collectorLevel(collector.status) : undefined}
          help={`chain head − 마지막 수집 블록. ${LAG_WARN_BLOCKS}블록 이하 정상, ${LAG_DANGER_BLOCKS}블록 이하 주의, 초과 시 위험. head는 수집 사이클의 관측값입니다.`}
        />
        <MetricCard
          label="처리량"
          value={kpis ? `${kpis.transactionsPerMinute.toFixed(1)}건/분` : "—"}
          sub={
            kpis
              ? `직전 5분 대비 ${formatDelta(kpis.transactionsDeltaPercent)}`
              : data.overviewError
                ? "지표 조회 실패"
                : "불러오는 중"
          }
          help="최근 5분 수집 트랜잭션 수 ÷ 5. 증감률은 직전 5분 구간과 비교하며 직전 구간이 0건이면 —로 표시합니다."
        />
        <MetricCard
          label="탐지율"
          value={kpis ? formatPercent(kpis.detectionRatePercent) : "—"}
          sub={
            kpis
              ? kpis.detectionRatePercent == null
                ? `최근 5분 수집 0건 (분모 없음) · 탐지 ${formatNumber(kpis.detectedLast5m)}건`
                : `최근 5분 탐지 ${formatNumber(kpis.detectedLast5m)}건`
              : data.overviewError
                ? "지표 조회 실패"
                : "불러오는 중"
          }
          help="최근 5분 탐지 이벤트 ÷ 수집 트랜잭션. 수집이 0건이면 0%로 속이지 않고 —로 표시합니다."
        />
        <MetricCard
          label="대응 Backlog"
          value={kpis ? `${formatNumber(kpis.backlogCount)}건` : "—"}
          sub={
            kpis
              ? kpis.oldestBacklogAgeSeconds != null
                ? `최장 대기 ${formatAge(kpis.oldestBacklogAgeSeconds)}`
                : "대기 중인 이벤트 없음"
              : data.overviewError
                ? "지표 조회 실패"
                : "불러오는 중"
          }
          level={kpis ? backlogLevel(kpis.backlogCount, kpis.oldestBacklogAgeSeconds) : undefined}
          help={`NEW + ACKNOWLEDGED 상태 이벤트 수. ${BACKLOG_WARN}건 초과 또는 30분 초과 대기 시 주의, ${BACKLOG_DANGER}건 초과 또는 2시간 초과 대기 시 위험.`}
        />
      </section>

      {/* C. Pipeline Throughput & Detection */}
      <section className="glass-card ops-section">
        <div className="section-head">
          <div>
            <p className="section-kicker">파이프라인 처리량</p>
            <h2>수집 · 탐지 시계열</h2>
          </div>
          <div className="range-toggle" role="group" aria-label="조회 범위 선택">
            {RANGE_OPTIONS.map((option) => (
              <button
                key={option.value}
                type="button"
                className={`range-button ${range === option.value ? "active" : ""}`}
                aria-pressed={range === option.value}
                onClick={() => setRange(option.value)}
              >
                {option.label}
              </button>
            ))}
          </div>
        </div>
        <DataState
          loading={data.loading}
          error={
            !overview && data.overviewError
              ? "시계열 지표를 불러오지 못했습니다."
              : null
          }
          onRetry={data.refresh}
        />
        {overview ? (
          <>
            {data.overviewError ? (
              <p className="stale-note" role="status">
                최신 조회에 실패해 마지막 성공 데이터를 표시 중입니다.
              </p>
            ) : null}
            <TimeSeriesChart
              series={overview.series}
              bucket={RANGE_BUCKETS[range]}
              ariaLabel={`최근 ${RANGE_OPTIONS.find((o) => o.value === range)?.label} 수집·탐지 시계열`}
            />
            {insight ? (
              <p className="insight-note" role="note">
                <strong>{formatDate(insight.bucketStart)} 구간</strong> — {SURGE_MESSAGES[insight.kind]}
              </p>
            ) : null}
          </>
        ) : null}
      </section>

      {/* D. Pipeline Health Strip */}
      <section className="glass-card ops-section">
        <div className="section-head compact">
          <div>
            <p className="section-kicker">파이프라인 상태</p>
            <h2>수집 → 탐지 → 알림 흐름</h2>
          </div>
          <a className="ghost-button" href="#/admin/pipeline">
            상세 보기
          </a>
        </div>
        {data.pipelineError && !pipeline ? (
          <DataState error="파이프라인 상태를 불러오지 못했습니다." onRetry={data.refresh} />
        ) : (
          <PipelineHealthStrip
            pipeline={pipeline}
            collector={collector}
            dltCount={kpis?.dltCount ?? null}
            loading={data.loading}
          />
        )}
      </section>

      {/* E. Risk × Status Matrix + 유형 Top 5 */}
      <section className="ops-two-col">
        <article className="glass-card">
          <div className="section-head compact">
            <div>
              <p className="section-kicker">위험도 × 처리 상태</p>
              <h2>탐지 이벤트 매트릭스</h2>
            </div>
          </div>
          <DataState
            loading={data.loading}
            error={!overview && data.overviewError ? "매트릭스를 불러오지 못했습니다." : null}
            empty={overview != null && overview.riskStatusMatrix.length === 0}
            emptyMessage="집계할 탐지 이벤트가 없습니다."
            onRetry={data.refresh}
          />
          {overview && overview.riskStatusMatrix.length > 0 ? (
            <RiskStatusMatrix cells={overview.riskStatusMatrix} />
          ) : null}
        </article>

        <article className="glass-card">
          <div className="section-head compact">
            <div>
              <p className="section-kicker">탐지 유형 Top 5</p>
              <h2>선택 기간 유형별 탐지</h2>
            </div>
          </div>
          <DataState
            loading={data.loading}
            error={!overview && data.overviewError ? "유형 집계를 불러오지 못했습니다." : null}
            onRetry={data.refresh}
          />
          {overview ? (
            <DistributionChart
              data={typeDistribution}
              emptyMessage="선택 기간에 탐지 이벤트가 없습니다."
              ariaLabel={`선택 기간(${RANGE_OPTIONS.find((o) => o.value === range)?.label}) 탐지 유형 Top 5 차트`}
            />
          ) : null}
        </article>
      </section>

      {/* F. 조사 큐 */}
      <section className="glass-card ops-section">
        <div className="section-head">
          <div>
            <p className="section-kicker">조사 큐</p>
            <h2>대응이 필요한 고위험 이벤트</h2>
          </div>
          <a className="ghost-button" href="#/events?riskLevel=CRITICAL">
            전체 보기
          </a>
        </div>
        <DataState
          loading={data.loading}
          error={data.queueError && !data.queue ? "조사 큐를 불러오지 못했습니다." : null}
          empty={data.queue != null && data.queue.length === 0}
          emptyMessage="CRITICAL/HIGH 등급의 이벤트가 없습니다."
          onRetry={data.refresh}
        />
        {data.queue && data.queue.length > 0 ? (
          <div className="table-scroll">
            <table className="data-table queue-table">
              <thead>
                <tr>
                  <th scope="col">위험도</th>
                  <th scope="col">점수</th>
                  <th scope="col">유형</th>
                  <th scope="col">주소</th>
                  <th scope="col">상태</th>
                  <th scope="col">대기</th>
                  <th scope="col">탐지 시각</th>
                </tr>
              </thead>
              <tbody>
                {data.queue.map((row) => (
                  <tr key={row.id}>
                    <td>
                      <RiskBadge riskLevel={row.riskLevel} riskScore={row.riskScore} />
                    </td>
                    <td className="num">{row.riskScore}</td>
                    <td>
                      <a className="queue-link" href={`#/events/${row.id}`} title={row.summary}>
                        {formatEventType(row.eventType)}
                      </a>
                    </td>
                    <td>
                      <span className="mono" title={row.walletAddress}>
                        {shortenAddress(row.walletAddress)}
                      </span>
                    </td>
                    <td>
                      <StatusBadge status={row.status} />
                    </td>
                    <td className="num">{formatAge(ageFrom(row.detectedAt))}</td>
                    <td className="num">{formatDate(row.detectedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </section>

      {/* G. 하단 보조 영역 */}
      <section className="ops-two-col">
        <article className="glass-card">
          <div className="section-head compact">
            <div>
              <p className="section-kicker">반복 탐지</p>
              <h2>탐지 다발 지갑</h2>
            </div>
          </div>
          <DataState
            loading={data.loading}
            error={data.statsError && !data.stats ? "지갑 통계를 불러오지 못했습니다." : null}
            empty={data.stats != null && data.stats.topWallets.length === 0}
            emptyMessage="반복 탐지된 지갑이 없습니다."
            onRetry={data.refresh}
          />
          {data.stats && data.stats.topWallets.length > 0 ? (
            <div className="table-scroll">
              <table className="data-table queue-table">
                <thead>
                  <tr>
                    <th scope="col">주소</th>
                    <th scope="col">탐지</th>
                    <th scope="col">최고 위험</th>
                    <th scope="col">마지막 탐지</th>
                  </tr>
                </thead>
                <tbody>
                  {data.stats.topWallets.map((wallet) => (
                    <tr key={wallet.walletAddress}>
                      <td>
                        <a
                          className="queue-link mono"
                          href={`#/wallets/${encodeURIComponent(wallet.walletAddress)}`}
                          title={wallet.walletAddress}
                        >
                          {shortenAddress(wallet.walletAddress, 10, 6)}
                        </a>
                      </td>
                      <td className="num">{formatNumber(wallet.eventCount)}건</td>
                      <td className="num">{wallet.maxRiskScore}</td>
                      <td className="num">
                        {wallet.lastDetectedAt ? formatDate(wallet.lastDetectedAt) : "—"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}
          {data.stats ? (
            <p className="summary-note">
              누적 탐지 {formatCompact(data.stats.totalEvents)}건 · 24시간 신규{" "}
              {formatCompact(data.stats.last24hEvents)}건 · 위험도{" "}
              {(data.stats.riskLevelCounts ?? [])
                .map((item) => `${RISK_LEVEL_LABELS[item.key] ?? item.key} ${formatCompact(item.count)}`)
                .join(" · ")}
            </p>
          ) : null}
        </article>

        <article className="glass-card">
          <div className="section-head compact">
            <div>
              <p className="section-kicker">실시간 피드</p>
              <h2>최근 수집/탐지 내역</h2>
            </div>
          </div>
          <DataState
            loading={data.loading}
            error={
              data.feedError && !data.eventFeed && !data.transactionFeed
                ? "실시간 피드를 불러오지 못했습니다."
                : null
            }
            onRetry={data.refresh}
          />
          <div className="feed-columns">
            <div className="notes-feed">
              {data.eventFeed != null && data.eventFeed.length === 0 ? (
                <div className="empty-state">최근 탐지 이벤트가 없습니다.</div>
              ) : null}
              {(data.eventFeed ?? []).map((item, index) => (
                <a
                  className="note-item linked"
                  key={`event-${item.eventId}-${index}`}
                  href={`#/events/${item.eventId}`}
                >
                  {formatEventType(item.eventType)} 감지, 위험 점수 {item.riskScore}
                  <small>{formatDate(item.detectedAt)}</small>
                </a>
              ))}
            </div>
            <div className="notes-feed">
              {data.transactionFeed != null && data.transactionFeed.length === 0 ? (
                <div className="empty-state">최근 수집된 트랜잭션이 없습니다.</div>
              ) : null}
              {(data.transactionFeed ?? []).map((item, index) => (
                <div className="note-item" key={`tx-${item.txHash}-${index}`}>
                  블록 {formatNumber(item.blockNumber)} ·{" "}
                  <span className="mono">{item.txHash.slice(0, 14)}...</span>
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
