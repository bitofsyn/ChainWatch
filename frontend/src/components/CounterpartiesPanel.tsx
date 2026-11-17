import { useEffect, useState } from "react";
import { ApiError, fetchWalletCounterparties } from "../api";
import { formatDate, shortenAddress } from "../lib/format";
import { formatNumber } from "../lib/opsOverview";
import type { Counterparty, WalletCounterparties } from "../types";

function formatEth(value: number): string {
  return `${Number(value).toLocaleString("ko-KR", { maximumFractionDigits: 6 })} ETH`;
}

/**
 * 지갑의 자금 흐름 조사 패널. 수집된 거래를 기준으로 이 지갑이 어디로 보냈고 어디서 받았는지
 * 상대 주소별 건수와 금액을 보여준다. 자금 분산·반복 이체 탐지를 판단할 때 쓴다.
 */
export function CounterpartiesPanel({ address }: { address: string }) {
  const [data, setData] = useState<WalletCounterparties | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setData(null);
    setError(null);
    fetchWalletCounterparties(address, 10)
      .then((result) => {
        if (active) {
          setData(result);
        }
      })
      .catch((cause) => {
        if (active) {
          setError(cause instanceof ApiError && cause.message ? cause.message : "거래 상대를 불러오지 못했습니다.");
        }
      });
    return () => {
      active = false;
    };
  }, [address]);

  return (
    <section className="panel-card">
      <div className="section-head compact">
        <div>
          <p className="section-kicker">자금 흐름</p>
          <h2>거래 상대</h2>
        </div>
      </div>
      <p className="cell-muted">
        수집된 거래 기준입니다. 수집하지 않은 구간의 거래는 포함되지 않습니다.
        {data
          ? ` 보낸 거래 ${formatNumber(data.outgoingTxCount)}건, 받은 거래 ${formatNumber(data.incomingTxCount)}건.`
          : ""}
      </p>
      {error ? (
        <div className="banner error" role="alert">
          {error}
        </div>
      ) : null}
      {!data && !error ? <p className="cell-muted">불러오는 중…</p> : null}
      {data ? (
        <div className="counterparty-grid">
          <CounterpartyTable title="보낸 곳 (상위 10)" rows={data.recipients} empty="보낸 거래가 없습니다." />
          <CounterpartyTable title="받은 곳 (상위 10)" rows={data.senders} empty="받은 거래가 없습니다." />
        </div>
      ) : null}
    </section>
  );
}

function CounterpartyTable({ title, rows, empty }: { title: string; rows: Counterparty[]; empty: string }) {
  return (
    <div>
      <h3 className="counterparty-title">{title}</h3>
      {rows.length === 0 ? (
        <p className="cell-muted">{empty}</p>
      ) : (
        <div className="table-scroll">
          <table className="data-table" aria-label={title}>
            <thead>
              <tr>
                <th scope="col">주소</th>
                <th scope="col" className="num">
                  건수
                </th>
                <th scope="col" className="num">
                  금액 합계
                </th>
                <th scope="col">마지막 거래</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.address}>
                  <td className="mono" title={row.address}>
                    <a className="row-link" href={`#/wallets/${row.address}`}>
                      {shortenAddress(row.address)}
                    </a>
                  </td>
                  <td className="num strong-num">{formatNumber(row.txCount)}</td>
                  <td className="num">{formatEth(row.totalAmount)}</td>
                  <td className="cell-time">{row.lastAt ? formatDate(row.lastAt) : "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
