import type { OpsCollector, PipelineComponent, PipelineStatus } from "../types";
import { formatCompact, formatNumber } from "../lib/opsOverview";

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

const STATUS_LABELS: Record<NodeStatus, string> = {
  UP: "정상",
  DOWN: "중단",
  DISABLED: "비활성",
  UNKNOWN: "미확인"
};

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
 * 클릭 시 관리자 파이프라인 상세로 이동한다.
 */
export function PipelineHealthStrip({ pipeline, collector, dltCount, loading }: PipelineHealthStripProps) {
  if (loading && !pipeline && !collector) {
    return <div className="data-state loading">파이프라인 상태 확인 중...</div>;
  }

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

  return (
    <ol className="health-strip" aria-label="파이프라인 구성요소 상태">
      {nodes.map((node, index) => (
        <li key={node.key} className="health-node-wrap">
          {index > 0 ? <span className="health-connector" aria-hidden="true" /> : null}
          <a className={`health-node status-${node.status.toLowerCase()}`} href="#/admin/pipeline">
            <span className="health-node-label">{node.label}</span>
            <span className="health-node-status">
              {STATUS_LABELS[node.status]}
              {node.impacted ? <em className="health-impacted">영향 가능</em> : null}
            </span>
            {node.sub ? <span className="health-node-sub">{node.sub}</span> : null}
          </a>
        </li>
      ))}
    </ol>
  );
}
