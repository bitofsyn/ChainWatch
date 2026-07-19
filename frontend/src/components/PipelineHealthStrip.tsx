import { useEffect, useRef, useState } from "react";
import type { OpsCollector, PipelineComponent, PipelineStatus } from "../types";
import { formatCompact, formatNumber } from "../lib/opsOverview";
import { usePrefersReducedMotion } from "../hooks/usePrefersReducedMotion";

interface PipelineHealthStripProps {
  pipeline: PipelineStatus | null;
  collector: OpsCollector | null;
  dltCount: number | null;
  loading: boolean;
}

type NodeStatus = "UP" | "DOWN" | "DISABLED" | "UNKNOWN";

interface StripNode {
  key: string;
  label: string;
  status: NodeStatus;
  /** 상류 장애로 영향을 받을 수 있는 정상 노드 */
  impacted: boolean;
  sub: string | null;
}

interface NodeChange {
  from: NodeStatus;
  to: NodeStatus;
  at: number;
}

const STATUS_LABELS: Record<NodeStatus, string> = {
  UP: "정상",
  DOWN: "중단",
  DISABLED: "비활성",
  UNKNOWN: "미확인"
};

/** 상태 전환 표시 유지 시간 (복구됨/중단 강조) */
const CHANGE_VISIBLE_MS = 8_000;

const changeTimeFormat = new Intl.DateTimeFormat("ko-KR", { hour: "2-digit", minute: "2-digit" });

function componentOf(pipeline: PipelineStatus | null, name: string): PipelineComponent | null {
  return pipeline?.components.find((item) => item.name === name) ?? null;
}

/** 복수 컴포넌트 합성: 하나라도 DOWN이면 DOWN, 전부 DISABLED면 DISABLED, 하나라도 UP이면 UP */
function combine(components: (PipelineComponent | null)[]): NodeStatus {
  const statuses = components.filter((item) => item != null).map((item) => item.status);
  if (statuses.length === 0) {
    return "UNKNOWN";
  }
  if (statuses.includes("DOWN")) {
    return "DOWN";
  }
  if (statuses.every((status) => status === "DISABLED")) {
    return "DISABLED";
  }
  return "UP";
}

/**
 * RPC → Collector → Kafka → Detection → 저장소 → 알림/AI 흐름의 상태 strip.
 * 각 노드는 /api/ops/pipeline의 구조화 status만 사용하고(detail 문자열 파싱 금지),
 * 상류 DOWN 이후의 정상 노드는 DOWN이 아니라 "영향 가능"으로 구분한다.
 * 상태가 실제로 변경된 노드만 1회 전환 강조 + 변경 시각을 표시한다
 * (polling마다 반복 애니메이션 금지, 흐르는 particle 금지).
 */
export function PipelineHealthStrip({ pipeline, collector, dltCount, loading }: PipelineHealthStripProps) {
  const reducedMotion = usePrefersReducedMotion();
  /** 노드별 마지막 관측 상태 (실제 변경 감지용) */
  const prevStatusesRef = useRef<Record<string, NodeStatus> | null>(null);
  const [recentChanges, setRecentChanges] = useState<Record<string, NodeChange>>({});

  const collectorComponent = componentOf(pipeline, "collector");
  const nodes: StripNode[] = [
    {
      key: "rpc",
      label: "Blockchain RPC",
      // RPC는 직접 probe하지 않고 수집 사이클의 head 관측 여부로 판단한다.
      status: collector ? (collector.chainHead != null ? "UP" : "UNKNOWN") : "UNKNOWN",
      impacted: false,
      sub: collector?.chainHead != null ? `head #${formatNumber(collector.chainHead)}` : "head 미관측"
    },
    {
      key: "collector",
      label: "Collector",
      status: (collectorComponent?.status as NodeStatus) ?? "UNKNOWN",
      impacted: false,
      sub:
        collector?.lagBlocks != null
          ? `lag ${formatCompact(collector.lagBlocks)}블록`
          : collector?.lastCollectedBlock != null
            ? `#${formatNumber(collector.lastCollectedBlock)} 수집`
            : "수집 이력 없음"
    },
    {
      key: "kafka",
      label: "Kafka",
      status: (componentOf(pipeline, "kafka")?.status as NodeStatus) ?? "UNKNOWN",
      impacted: false,
      sub: dltCount == null ? "DLT 측정 불가" : `DLT ${formatNumber(dltCount)}건`
    },
    {
      key: "detection",
      label: "Detection Engine",
      status: (componentOf(pipeline, "detection")?.status as NodeStatus) ?? "UNKNOWN",
      impacted: false,
      sub: null
    },
    {
      key: "storage",
      label: "PostgreSQL·Redis",
      status: combine([componentOf(pipeline, "database"), componentOf(pipeline, "redis")]),
      impacted: false,
      sub: null
    },
    {
      key: "notify",
      label: "Notification·AI",
      status: combine([componentOf(pipeline, "notification"), componentOf(pipeline, "aiServer")]),
      impacted: false,
      sub: null
    }
  ];

  // 상류 DOWN 이후의 UP 노드는 "영향 가능"으로 표시한다(무조건 DOWN으로 오표기하지 않음).
  let upstreamDown = false;
  for (const node of nodes) {
    if (upstreamDown && node.status === "UP") {
      node.impacted = true;
    }
    if (node.status === "DOWN") {
      upstreamDown = true;
    }
  }

  // 실제 상태 변경만 감지한다. 최초 관측(prev 없음)과 polling 반복은 전환이 아니다.
  const statusSignature = nodes.map((node) => `${node.key}:${node.status}`).join("|");
  useEffect(() => {
    const previous = prevStatusesRef.current;
    const current = Object.fromEntries(nodes.map((node) => [node.key, node.status]));
    prevStatusesRef.current = current;
    if (previous == null) {
      return;
    }
    const changed = nodes.filter(
      (node) => previous[node.key] != null && previous[node.key] !== node.status
    );
    if (changed.length === 0) {
      return;
    }
    const at = Date.now();
    setRecentChanges((existing) => {
      const next = { ...existing };
      for (const node of changed) {
        next[node.key] = { from: previous[node.key], to: node.status, at };
      }
      return next;
    });
    const timer = setTimeout(() => {
      setRecentChanges((existing) => {
        const next = { ...existing };
        for (const node of changed) {
          if (next[node.key]?.at === at) {
            delete next[node.key];
          }
        }
        return next;
      });
    }, CHANGE_VISIBLE_MS);
    return () => clearTimeout(timer);
    // statusSignature가 실제 변경을 대표한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusSignature]);

  if (loading && !pipeline && !collector) {
    return <div className="data-state loading">파이프라인 상태 확인 중...</div>;
  }

  return (
    <ol className="health-strip" aria-label="파이프라인 구성요소 상태">
      {nodes.map((node, index) => {
        const change = recentChanges[node.key];
        const recovered = change != null && change.from === "DOWN" && change.to === "UP";
        const wentDown = change != null && change.to === "DOWN";
        return (
          <li key={node.key} className="health-node-wrap">
            {index > 0 ? <span className="health-connector" aria-hidden="true" /> : null}
            <a
              className={`health-node status-${node.status.toLowerCase()} ${
                change != null && !reducedMotion
                  ? wentDown
                    ? "just-down"
                    : recovered
                      ? "just-recovered"
                      : "just-changed"
                  : ""
              }`}
              href="#/admin/pipeline"
            >
              <span className="health-node-label">{node.label}</span>
              <span className="health-node-status">
                {STATUS_LABELS[node.status]}
                {recovered ? <em className="health-recovered">복구됨</em> : null}
                {node.impacted ? <em className="health-impacted">영향 가능</em> : null}
              </span>
              {change != null ? (
                <span className="health-change-time">
                  {changeTimeFormat.format(change.at)} 상태 변경
                </span>
              ) : node.sub ? (
                <span className="health-node-sub">{node.sub}</span>
              ) : null}
            </a>
          </li>
        );
      })}
    </ol>
  );
}
