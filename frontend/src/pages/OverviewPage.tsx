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
import type { ValueChange } from "../lib/overviewDiff";
import { KPI_SEMANTICS } from "../lib/overviewDiff";
import { navigate, overviewPath, parseOverviewRange } from "../lib/router";
import { formatDate, formatEventType, RISK_LEVEL_LABELS, shortenAddress } from "../lib/format";
import { MetricCard } from "../components/MetricCard";
import { AnimatedMetricValue } from "../components/AnimatedMetricValue";
import { LiveStatusCluster } from "../components/LiveStatusCluster";
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

const generatedFormat = new Intl.DateTimeFormat("ko-KR", {
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit"
});

/* ── delta chip 포맷터: 단위를 생략하지 않는다 ── */

function signOf(delta: number): string {
  return delta > 0 ? "+" : "−";
}

function chipBlocks(change: ValueChange): string | null {
  return change.delta == null ? null : `${signOf(change.delta)}${formatNumber(Math.abs(Math.round(change.delta)))}블록`;
}

function chipPerMinute(change: ValueChange): string | null {
  return change.delta == null ? null : `${signOf(change.delta)}${Math.abs(change.delta).toFixed(1)}건/분`;
}

function chipPercentPoint(change: ValueChange): string | null {
  return change.delta == null ? null : `${signOf(change.delta)}${Math.abs(change.delta).toFixed(1)}%p`;
}

function chipCount(change: ValueChange): string | null {
  return change.delta == null ? null : `${signOf(change.delta)}${formatNumber(Math.abs(Math.round(change.delta)))}건`;
}

interface OverviewPageProps {
  /** 현재 해시 라우트 ("/", "/?range=6h"). range를 URL에 보존한다. */
  route: string;
}

export function OverviewPage({ route }: OverviewPageProps) {
  // 선택 range는 URL query에 보존한다: 새로고침/공유/브라우저 뒤로가기에서 유지된다.
  const range = parseOverviewRange(route) ?? "24h";
  const setRange = (next: OpsRange) => {
    if (next !== range) {
      navigate(overviewPath(next));
    }
  };
  const data = useOverviewData(range);
  const { overview, pipeline } = data;

  const status = overallStatus(
    pipeline?.components ?? null,
    overview?.collector ?? null,
    overview?.kpis ?? null
  );
  // insight와 차트 anomaly marker는 같은 계산 결과를 공유한다(single source of truth).
  const insight = overview ? throughputInsight(overview.series) : null;

  const typeDistribution = (overview?.eventTypes ?? []).map((item) => ({
    key: item.key,
    label: formatEventType(item.key),
    count: item.count
  }));

  const collector = overview?.collector ?? null;
  const kpis = overview?.kpis ?? null;
  const changes = data.kpiChanges;

  // range 변경 조회가 진행 중이면 기존 차트 위에 작은 overlay만 띄운다.
  const rangeUpdating = overview != null && overview.range !== range;

  const newCriticalCount = (data.queue ?? []).filter(
    (row) => row.riskLevel === "CRITICAL" && data.newQueueIds.has(String(row.id))
  ).length;

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
          {newCriticalCount > 0 ? (
            <span className="ops-critical-badge" role="status">
              신규 CRITICAL {newCriticalCount}건
            </span>
          ) : null}
          <LiveStatusCluster
            hasData={data.lastSuccessAt != null}
            refreshing={data.refreshing}
            resyncing={data.resyncing}
            paused={data.paused}
            allFailed={data.allFailed}
            anyError={
              data.overviewError ||
              data.pipelineError ||
              data.statsError ||
              data.queueError ||
              data.feedError
            }
            lastSuccessAt={data.lastSuccessAt}
            nextRefreshAt={data.nextRefreshAt}
            onRefresh={data.refresh}
          />
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
          value={
            <AnimatedMetricValue
              value={collector?.lagBlocks ?? null}
              format={(value) => (value == null ? "—" : `${formatNumber(Math.round(value))} 블록`)}
              change={changes?.lagBlocks ?? null}
            />
          }
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
          change={changes?.lagBlocks ?? null}
          semantic={KPI_SEMANTICS.lagBlocks}
          formatChipDelta={chipBlocks}
          help={`chain head − 마지막 수집 블록. ${LAG_WARN_BLOCKS}블록 이하 정상, ${LAG_DANGER_BLOCKS}블록 이하 주의, 초과 시 위험. head는 수집 사이클의 관측값입니다.`}
        />
        <MetricCard
          label="처리량"
          value={
            <AnimatedMetricValue
              value={kpis?.transactionsPerMinute ?? null}
              format={(value) => (value == null ? "—" : `${value.toFixed(1)}건/분`)}
              change={changes?.transactionsPerMinute ?? null}
            />
          }
          sub={
            kpis
              ? `직전 5분 대비 ${formatDelta(kpis.transactionsDeltaPercent)}`
              : data.overviewError
                ? "지표 조회 실패"
                : "불러오는 중"
          }
          change={changes?.transactionsPerMinute ?? null}
          semantic={KPI_SEMANTICS.transactionsPerMinute}
          formatChipDelta={chipPerMinute}
          help="최근 5분 수집 트랜잭션 수 ÷ 5. 증감률은 직전 5분 구간과 비교하며 직전 구간이 0건이면 —로 표시합니다."
        />
        <MetricCard
          label="탐지율"
          value={
            <AnimatedMetricValue
              value={kpis?.detectionRatePercent ?? null}
              format={(value) => formatPercent(value)}
              change={changes?.detectionRatePercent ?? null}
            />
          }
          sub={
            kpis
              ? kpis.detectionRatePercent == null
                ? `최근 5분 수집 0건 (분모 없음) · 탐지 ${formatNumber(kpis.detectedLast5m)}건`
                : `최근 5분 탐지 ${formatNumber(kpis.detectedLast5m)}건`
              : data.overviewError
                ? "지표 조회 실패"
                : "불러오는 중"
          }
          change={changes?.detectionRatePercent ?? null}
          semantic={KPI_SEMANTICS.detectionRatePercent}
          formatChipDelta={chipPercentPoint}
          help="최근 5분 탐지 이벤트 ÷ 수집 트랜잭션. 수집이 0건이면 0%로 속이지 않고 —로 표시합니다."
        />
        <MetricCard
          label="대응 Backlog"
          value={
            <AnimatedMetricValue
              value={kpis?.backlogCount ?? null}
              format={(value) => (value == null ? "—" : `${formatNumber(Math.round(value))}건`)}
              change={changes?.backlogCount ?? null}
            />
          }
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
          change={changes?.backlogCount ?? null}
          semantic={KPI_SEMANTICS.backlogCount}
          formatChipDelta={chipCount}
          help={`CRITICAL/HIGH 등급의 NEW + ACKNOWLEDGED 이벤트 수 (저위험은 자동 만료 대상이라 제외). ${BACKLOG_WARN}건 초과 또는 30분 초과 대기 시 주의, ${BACKLOG_DANGER}건 초과 또는 2시간 초과 대기 시 위험.`}
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
                <span className="stale-badge">STALE</span> 최신 조회에 실패해{" "}
                {generatedFormat.format(new Date(overview.generatedAt))} 집계 데이터를 표시 중입니다.
              </p>
            ) : null}
            <TimeSeriesChart
              series={overview.series}
              bucket={RANGE_BUCKETS[range]}
              ariaLabel={`최근 ${RANGE_OPTIONS.find((o) => o.value === range)?.label} 수집·탐지 시계열`}
              queryKey={`${overview.range}/${overview.bucket}`}
              generatedAt={overview.generatedAt}
              anomaly={insight}
              newBuckets={data.newBuckets}
              updating={rangeUpdating}
            />
            <p className="chart-meta">
              서버 집계 {generatedFormat.format(new Date(overview.generatedAt))} 기준 · 시간대는
              브라우저 로컬 기준
            </p>
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
                  <tr
                    key={row.id}
                    className={data.newQueueIds.has(String(row.id)) ? "row-new" : ""}
                  >
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
              {(data.eventFeed ?? []).map((item) => (
                <a
                  className={`note-item linked ${
                    data.newEventFeedIds.has(String(item.eventId)) ? "row-new" : ""
                  }`}
                  key={`event-${item.eventId}`}
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
              {(data.transactionFeed ?? []).map((item) => (
                <div
                  className={`note-item ${
                    data.newTransactionKeys.has(item.txHash) ? "row-new" : ""
                  }`}
                  key={`tx-${item.txHash}`}
                >
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
