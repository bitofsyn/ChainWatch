import { ReconciliationPanel } from "./ReconciliationPanel";
import { useEffect, useState } from "react";
import { ApiError, fetchStakingEvidence, fetchStakingObservations, observeStakingValidators, type StakingOverview } from "../api";
import { useAuth } from "../contexts/AuthContext";

const OBSERVATION_STATUS: Record<string, string> = {
  OBSERVED: "조회 완료",
  FAILED: "조회 실패",
  RUNNING: "조회 중"
};

function observationStatus(status: string) {
  return OBSERVATION_STATUS[status] ?? status;
}

export function StakingPage() {
  const { user } = useAuth();
  const [data, setData] = useState<StakingOverview | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [evidence, setEvidence] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const message = (e: unknown) => e instanceof ApiError && (e.status === 401 || e.status === 403)
    ? "로그인과 운영 조회 권한이 필요합니다." : "검증인 상태를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.";
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
        setNotice(result.status === "OBSERVED" ? "최신 검증인 상태를 저장했습니다." : `상태 조회를 완료하지 못했습니다: ${result.errorCode ?? observationStatus(result.status)}`);
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
      <p className="eyebrow">ETHEREUM · VALIDATOR</p>
      <h1>검증인 상태 확인</h1>
      <p className="page-lede">이더리움 네트워크에서 선택한 검증인의 현재 상태와 잔액을 확인하고, 잔액 변화가 보상·패널티·출금 내역과 맞는지 검산합니다.</p>
    </div></section>
    <section className="panel staking-panel">
      <div className="staking-actions">
        <strong>{data?.network ?? "네트워크 확인 중"}</strong>
        <button className="ghost-button" disabled={busy} onClick={() => void refresh()}>목록 새로고침</button>
        {user?.role === "ADMIN" && <button className="ghost-button" disabled={busy || !data?.enabled} onClick={() => void refresh(true)}>
          {busy ? "조회 중…" : "최신 상태 가져오기"}
        </button>}
      </div>
      {error && <p role="alert">{error}</p>}
      {notice && <p role="status">{notice}</p>}
      {!data && !error && <p role="status">검증인 상태 기록을 불러오는 중입니다.</p>}
      {data && !data.enabled && <div className="staking-onboarding" role="status">
        <h2>현재 검증인 조회 기능이 꺼져 있습니다</h2>
        <p>이 기능은 Beacon API를 제공하는 이더리움 합의 클라이언트와 조회할 검증인 번호가 있어야 동작합니다. 일반 거래 모니터링 기능과는 별개의 선택 기능입니다.</p>
        <ol>
          <li>Teku, Lighthouse 같은 합의 클라이언트의 Beacon API 주소를 준비합니다.</li>
          <li><code>.env</code>에 네트워크, genesis root, 검증인 번호와 활성화 옵션을 입력합니다.</li>
          <li>Docker 스택을 다시 시작한 뒤 <strong>최신 상태 가져오기</strong>를 누릅니다.</li>
        </ol>
        <p><a className="ghost-button" href="#/demo">설정 예시와 사용 순서 보기</a></p>
      </div>}
      {data?.enabled && !latest && <p>아직 저장된 상태가 없습니다. 관리자가 <strong>최신 상태 가져오기</strong>를 누르면 결과가 표시됩니다.</p>}
      {latest && <p>최근 조회: <strong>{observationStatus(latest.status)}</strong> · {new Date(latest.startedAt).toLocaleString()}
        {latest.errorCode && ` · ${latest.errorCode}`}{latest.stale && " · 오래된 데이터"}</p>}
      {successful && <>
        <h2>최근 정상 조회 결과</h2>
        <p>{new Date(successful.startedAt).toLocaleString()} · {successful.clientVersion}
          {successful.stale && " · 오래된 데이터"}{latest?.id !== successful.id && " · 최근 시도 결과와 다릅니다"}</p>
        <p>Slot {successful.slot ?? "—"} · Epoch {successful.epoch ?? "—"} · 체인 시각 {successful.stateTime ? new Date(successful.stateTime).toLocaleString() : "—"}</p>
        <p className="staking-root">State root: {successful.stateRoot}</p>
        <div className="staking-table"><table><thead><tr>
          <th>검증인 번호</th><th>상태</th><th>현재 잔액 (gwei)</th><th>보상 계산 기준 잔액 (gwei)</th><th>규칙 위반 삭감</th>
        </tr></thead><tbody>{successful.validators?.map(v => <tr key={v.index}>
          <td title={v.publicKey}>{v.index}</td><td>{v.status}</td><td>{v.balanceGwei}</td><td>{v.effectiveBalanceGwei}</td><td>{v.slashed ? "예" : "아니오"}</td>
        </tr>)}</tbody></table></div>
        <button className="ghost-button" disabled={busy} onClick={() => void showEvidence(successful.id)}>조회에 사용한 원본 데이터 보기</button>
      </>}
      {evidence && <pre className="staking-evidence">{evidence}</pre>}
    </section>
    <ReconciliationPanel />
    {data && data.observations.length > 0 && <section className="panel staking-panel"><h2>최근 상태 조회 이력</h2>
      <div className="staking-table"><table><thead><tr><th>시도 시각</th><th>결과</th><th>오류</th><th>신선도</th></tr></thead>
      <tbody>{data.observations.map(run => <tr key={run.id}><td>{new Date(run.startedAt).toLocaleString()}</td>
        <td>{observationStatus(run.status)}</td><td>{run.errorCode ?? "—"}</td><td>{run.stale ? "오래됨" : "최근"}</td></tr>)}</tbody></table></div>
    </section>}
  </>;
}
