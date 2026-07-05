import { formatLifecycleStatus } from "../lib/format";

interface StatusBadgeProps {
  status: string;
}

const STATUS_CLASS: Record<string, string> = {
  NEW: "lifecycle-new",
  ACKNOWLEDGED: "lifecycle-ack",
  INVESTIGATING: "lifecycle-investigating",
  RESOLVED: "lifecycle-resolved"
};

export function StatusBadge({ status }: StatusBadgeProps) {
  return (
    <span className={`status-pill ${STATUS_CLASS[status] ?? "lifecycle-new"}`}>
      {formatLifecycleStatus(status)}
    </span>
  );
}
