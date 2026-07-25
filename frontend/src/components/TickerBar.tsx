import { useEffect, useState } from "react";
import { fetchOpsOverview } from "../api";
import type { OpsOverview } from "../types";
import {
  backlogLevel,
  collectorLevel,
  formatNumber,
  formatPercent,
  type KpiLevel
} from "../lib/opsOverview";

const POLL_MS = 30_000;

const LEVEL_TONE: Record<KpiLevel, string> = {
  ok: "tone-ok",
  warn: "tone-warn",
  danger: "tone-danger",
  unknown: ""
};

/**
 * 거래소 터미널식 실시간 티커 바. 핵심 운영 수치(블록·lag·처리량·탐지율·backlog)를
 * 네비 아래 한 줄로 상시 노출한다. 데이터를 한 번도 못 받으면(미인증·백엔드 다운)
 * 조용히 렌더하지 않고, 갱신 실패 시엔 마지막 값에 STALE 표시로 강등한다.
 */
export function TickerBar() {
  const [data, setData] = useState<OpsOverview | null>(null);
  const [stale, setStale] = useState(false);

  useEffect(() => {
    let alive = true;
    async function load() {
      try {
        // 백엔드 allowlist에 있는 조합만 유효하다 (1h 창은 5m 버킷)
        const next = await fetchOpsOverview("1h", "5m");
        if (alive) {
          setData(next);
          setStale(false);
        }
      } catch {
        if (alive) {
          setStale(true);
        }
      }
    }
    load();
    const timer = setInterval(load, POLL_MS);
    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, []);

  if (!data) {
    return null;
  }

  const { collector, kpis } = data;
  const lagTone = LEVEL_TONE[collectorLevel(collector.status)];
  const backlogTone = LEVEL_TONE[backlogLevel(kpis.backlogCount, kpis.oldestBacklogAgeSeconds)];

  return (
    <div className="ticker-bar" role="status" aria-label="실시간 운영 티커">
      <span className={`ticker-live ${stale ? "stale" : ""}`}>{stale ? "STALE" : "LIVE"}</span>
      <span className="ticker-item">
        <span className="ticker-label">BLOCK</span>
        <span className="ticker-value">
          {collector.lastCollectedBlock != null
            ? `#${formatNumber(collector.lastCollectedBlock)}`
            : "—"}
        </span>
      </span>
      <span className="ticker-item">
        <span className="ticker-label">LAG</span>
        <span className={`ticker-value ${lagTone}`}>{formatNumber(collector.lagBlocks)}</span>
      </span>
      <span className="ticker-item">
        <span className="ticker-label">TPM</span>
        <span className="ticker-value">{kpis.transactionsPerMinute.toFixed(1)}</span>
      </span>
      <span className="ticker-item">
        <span className="ticker-label">DETECT</span>
        <span className="ticker-value">{formatPercent(kpis.detectionRatePercent)}</span>
      </span>
      <span className="ticker-item">
        <span className="ticker-label">BACKLOG</span>
        <span className={`ticker-value ${backlogTone}`}>{formatNumber(kpis.backlogCount)}</span>
      </span>
    </div>
  );
}
