import { ReconciliationPanel } from "./ReconciliationPanel";
import { useEffect, useState } from "react";
import { ApiError, fetchStakingEvidence, fetchStakingObservations, observeStakingValidators, type StakingOverview } from "../api";
import { useAuth } from "../contexts/AuthContext";

export function StakingPage() {
  const { user } = useAuth();
  const [data, setData] = useState<StakingOverview | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [evidence, setEvidence] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const message = (e: unknown) => e instanceof ApiError && (e.status === 401 || e.status === 403)
    ? "로그인과 운영 조회 권한이 필요합니다." : "관측 요청 또는 조회에 실패했습니다. 다시 시도해주세요.";
  useEffect(() => {
    let active = true;
    fetchStakingObservations().then(result => { if (active) setData(result); })
      .catch(e => { if (active) setError(message(e)); });
    return () => { active = false; };
  }, []);
  async function refresh(observe = false) {
    setBusy(true); setError(null); setEvidence(null); setNotice(null);
    try {
      if (observe) {
        const result = await observeStakingValidators();
        setNotice(result.status === "OBSERVED" ? "관측 데이터를 저장했습니다." : `관측이 완료되지 않았습니다: ${result.errorCode ?? result.status}`);
      }
      setData(await fetchStakingObservations());
    } catch (e) { setError(message(e)); }
    finally { setBusy(false); }
  }
  async function showEvidence(id: number) {
    setBusy(true); setError(null);
    try { setEvidence(JSON.stringify(await fetchStakingEvidence(id), null, 2)); }
    catch (e) { setError(message(e)); }
    finally { setBusy(false); }
  }
  const latest = data?.observations[0];
  const successful = data?.observations.find(item => item.status === "OBSERVED");
  return <>
    <section className="page-head"><div>
      <p className="eyebrow">STAKING · OBSERVATION</p>
      <h1>검증인 관측</h1>
      <p className="page-lede">확정된 체인 상태를 기준으로 검증인의 상태와 잔액을 확인합니다. 보상 정산과 지급은 아직 지원하지 않습니다.</p>
    </div></section>
    <section className="panel staking-panel">
      <div className="staking-actions">
        <strong>{data?.network ?? "네트워크 확인 중"}</strong>
        <button className="ghost-button" disabled={busy} onClick={() => void refresh()}>목록 새로고침</button>
        {user?.role === "ADMIN" && <button className="ghost-button" disabled={busy || !data?.enabled} onClick={() => void refresh(true)}>
          {busy ? "처리 중…" : "검증인 관측 실행"}
        </button>}
      </div>
      {error && <p role="alert">{error}</p>}
      {notice && <p role="status">{notice}</p>}
      {!data && !error && <p role="status">관측 이력을 불러오는 중입니다.</p>}
      {data && !data.enabled && <p>관측이 비활성화되어 있습니다. 운영자가 네트워크와 관측 대상을 설정한 뒤 사용할 수 있습니다.</p>}
      {data?.enabled && !latest && <p>아직 관측 이력이 없습니다. 운영자가 관측을 실행하면 결과가 표시됩니다.</p>}
      {latest && <p>최근 시도: <strong>{latest.status}</strong> · {new Date(latest.startedAt).toLocaleString()}
        {latest.errorCode && ` · ${latest.errorCode}`}{latest.stale && " · 오래된 관측"}</p>}
      {successful && <>
        <h2>마지막 성공 관측</h2>
        <p>{new Date(successful.startedAt).toLocaleString()} · {successful.clientVersion}
          {successful.stale && " · 오래된 데이터"}{latest?.id !== successful.id && " · 최근 시도 결과와 다릅니다"}</p>
        <p>Slot {successful.slot ?? "—"} · Epoch {successful.epoch ?? "—"} · 체인 시각 {successful.stateTime ? new Date(successful.stateTime).toLocaleString() : "—"}</p>
        <p className="staking-root">State root: {successful.stateRoot}</p>
        <div className="staking-table"><table><thead><tr>
          <th>검증인</th><th>상태</th><th>잔액 (gwei)</th><th>유효 잔액 (gwei)</th><th>Slashed</th>
        </tr></thead><tbody>{successful.validators?.map(v => <tr key={v.index}>
          <td title={v.publicKey}>{v.index}</td><td>{v.status}</td><td>{v.balanceGwei}</td><td>{v.effectiveBalanceGwei}</td><td>{v.slashed ? "예" : "아니오"}</td>
        </tr>)}</tbody></table></div>
        <button className="ghost-button" disabled={busy} onClick={() => void showEvidence(successful.id)}>원본 관측 근거 보기</button>
      </>}
      {evidence && <pre className="staking-evidence">{evidence}</pre>}
    </section>
    <ReconciliationPanel />
    {data && data.observations.length > 0 && <section className="panel staking-panel"><h2>최근 관측 이력</h2>
      <div className="staking-table"><table><thead><tr><th>시도 시각</th><th>결과</th><th>오류</th><th>신선도</th></tr></thead>
      <tbody>{data.observations.map(run => <tr key={run.id}><td>{new Date(run.startedAt).toLocaleString()}</td>
        <td>{run.status}</td><td>{run.errorCode ?? "—"}</td><td>{run.stale ? "오래됨" : "최근"}</td></tr>)}</tbody></table></div>
    </section>}
  </>;
}
