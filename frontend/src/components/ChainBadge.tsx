import { formatNetwork } from "../lib/format";

/** 이벤트/트랜잭션이 속한 체인을 나타내는 배지. 체인별 색상 도트로 구분한다. */
export function ChainBadge({ network }: { network: string | null | undefined }) {
  const key = network || "ethereum-mainnet";
  return (
    <span className={`chain-badge chain-${key}`} title={key}>
      <span className="chain-dot" aria-hidden="true" />
      {formatNetwork(network)}
    </span>
  );
}
