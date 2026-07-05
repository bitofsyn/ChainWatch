import { startTransition, useEffect, useState } from "react";
import { ApiError, fetchTransaction } from "../api";
import type { TransactionItem } from "../types";
import { formatDate } from "../lib/format";

interface TransactionDetailPageProps {
  transactionId: number;
}

export function TransactionDetailPage({ transactionId }: TransactionDetailPageProps) {
  const [transaction, setTransaction] = useState<TransactionItem | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function load() {
      setLoading(true);
      setTransaction(null);
      try {
        const data = await fetchTransaction(transactionId);
        if (!active) {
          return;
        }
        startTransition(() => {
          setTransaction(data);
          setError(null);
          setLoading(false);
        });
      } catch (cause) {
        if (!active) {
          return;
        }
        const notFound = cause instanceof ApiError && cause.status === 404;
        startTransition(() => {
          setError(
            notFound
              ? `트랜잭션 #${transactionId}를 찾을 수 없습니다.`
              : "트랜잭션 조회에 실패했습니다. 백엔드 상태를 확인해주세요."
          );
          setLoading(false);
        });
      }
    }

    load();
    return () => {
      active = false;
    };
  }, [transactionId]);

  return (
    <>
      <section className="page-head">
        <div>
          <a className="back-link" href="#/events">
            ← 이상거래 목록
          </a>
          <p className="eyebrow">트랜잭션 상세</p>
          <h1>트랜잭션 #{transactionId}</h1>
        </div>
        {error ? <div className="banner error">{error}</div> : null}
      </section>

      {loading ? <div className="empty-state">불러오는 중...</div> : null}

      {transaction ? (
        <section className="detail-grid">
          <article className="glass-card">
            <div className="section-head compact">
              <div>
                <p className="section-kicker">온체인 기록</p>
                <h2>트랜잭션 정보</h2>
              </div>
            </div>
            <dl className="detail-list">
              <div>
                <dt>해시</dt>
                <dd className="mono">{transaction.txHash}</dd>
              </div>
              <div>
                <dt>블록 번호</dt>
                <dd>{transaction.blockNumber}</dd>
              </div>
              <div>
                <dt>체결 시각</dt>
                <dd>{formatDate(transaction.timestamp)}</dd>
              </div>
              <div>
                <dt>금액</dt>
                <dd>{transaction.amount} ETH</dd>
              </div>
              <div>
                <dt>가스 수수료</dt>
                <dd>{transaction.gasFee} ETH</dd>
              </div>
              <div>
                <dt>컨트랙트</dt>
                <dd className="mono">{transaction.contractAddress ?? "-"}</dd>
              </div>
            </dl>
          </article>

          <article className="glass-card">
            <div className="section-head compact">
              <div>
                <p className="section-kicker">연관 지갑</p>
                <h2>송수신 주소</h2>
              </div>
            </div>
            <dl className="detail-list">
              <div>
                <dt>보낸 주소</dt>
                <dd className="mono">
                  <a href={`#/wallets/${encodeURIComponent(transaction.fromAddress)}`}>
                    {transaction.fromAddress}
                  </a>
                </dd>
              </div>
              <div>
                <dt>받는 주소</dt>
                <dd className="mono">
                  {transaction.toAddress ? (
                    <a href={`#/wallets/${encodeURIComponent(transaction.toAddress)}`}>
                      {transaction.toAddress}
                    </a>
                  ) : (
                    "-"
                  )}
                </dd>
              </div>
            </dl>
            <p className="hint-text">
              주소를 선택하면 해당 지갑의 반복 탐지 이력과 수집된 트랜잭션을 확인할 수 있습니다.
            </p>
          </article>
        </section>
      ) : null}
    </>
  );
}
