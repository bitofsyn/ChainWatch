import { useEffect, useState } from "react";
import { fetchReconciliations, reconcileEpoch, fetchReconciliationEvidence, type ReconciliationRun } from "../api";
import { useAuth } from "../contexts/AuthContext";

export function ReconciliationPanel() {
  const { user } = useAuth();
  const [runs, setRuns] = useState<ReconciliationRun[]>([]);
  const [enabled, setEnabled] = useState(false);
  const [epoch, setEpoch] = useState("1");
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [evidence, setEvidence] = useState<string | null>(null);
  useEffect(() => { let active = true; fetchReconciliations().then(d => { if (active) {setRuns(d.runs); setEnabled(d.enabled);} })
    .catch(() => {if(active) setError("대사 이력을 불러오지 못했습니다.");}); return () => {active=false;}; }, []);
  async function load(execute = false) {
    setBusy(true); setError(null); setEvidence(null);
    try {
      if (execute) {const result = await reconcileEpoch(Number(epoch));setSelectedId(result.id);}
      const d = await fetchReconciliations();setRuns(d.runs);setEnabled(d.enabled);
    } catch {setError("대사 요청에 실패했습니다. 실행 중인 작업과 네트워크·권한을 확인해주세요.");}
    finally {setBusy(false);}
  }
  const selected = runs.find(r => r.id === selectedId) ?? runs[0];
  return <section className="panel staking-panel">
    <h2>Epoch 보상·패널티 대사</h2>
    <p>로컬 minimal 개발망 전용입니다. 잔액 변화와 보상·패널티·출금을 비교하며, 일치해도 지급이나 원장 반영은 실행하지 않습니다.</p>
    <p>구간은 시작 slot 처리 후 → 다음 epoch 시작 slot 처리 후입니다. 증명 보상은 입력 epoch의 이전 epoch를 대상으로 합니다.</p>
    <div className="staking-actions">
      <label>대사 epoch <input aria-label="대사 epoch" type="number" min="1" max="1000000" value={epoch} onChange={e=>setEpoch(e.target.value)} /></label>
      {user?.role === "ADMIN" && <button className="ghost-button" disabled={busy || !enabled || !/^[1-9][0-9]*$/.test(epoch) || Number(epoch)>1000000} onClick={()=>void load(true)}>{busy ? "대사 중…" : "대사 실행"}</button>}
      <button className="ghost-button" disabled={busy} onClick={()=>void load()}>대사 이력 새로고침</button>
    </div>
    {!enabled && <p>실행 노드 연결과 대사 기능 활성화가 필요합니다.</p>}
    {error && <p role="alert">{error}</p>}
    {enabled && !runs.length && <p>아직 대사 이력이 없습니다.</p>}
    {selected && <>
      <p role="status">Epoch {selected.accountingEpoch}: <strong>{selected.status}</strong>{selected.errorCode && ` · ${selected.errorCode}`}</p>
      <p>Slot {selected.report?.startSlot ?? "—"} → {selected.report?.endSlot ?? "—"} · 연결 검증 블록 {selected.report?.linkedBlocks ?? "—"}개 · 정산 반영 안 함</p>
      {selected.report?.validators && <p>오른쪽으로 스크롤하면 잔액과 보상 구성 항목을 볼 수 있습니다. 금액 단위는 모두 gwei입니다.</p>}
      {selected.report?.validators && <div className="staking-table"><table><thead><tr>
        <th>검증인</th><th>보상 gwei</th><th>패널티 gwei</th><th>차액 gwei</th><th>기초 gwei</th><th>기말 gwei</th><th>증명 순액</th><th>제안 보상</th><th>동기화 순액</th><th>출금</th>
      </tr></thead><tbody>{selected.report.validators.map(r=><tr key={r.validatorIndex}>
        <td>{r.validatorIndex}</td><td>{r.consensusRewardsGwei}</td><td>{r.consensusPenaltiesGwei}</td><td>{r.residualGwei}</td><td>{r.openingGwei}</td><td>{r.closingGwei}</td><td>{r.attestationNetGwei}</td><td>{r.proposerRewardGwei}</td><td>{r.syncNetGwei}</td><td>{r.withdrawalsGwei}</td>
      </tr>)}</tbody></table></div>}
      <button className="ghost-button" disabled={busy} onClick={()=>{
        setBusy(true);setError(null);fetchReconciliationEvidence(selected.id).then(d=>setEvidence(JSON.stringify(d,null,2)))
          .catch(()=>setError("대사 근거를 불러오지 못했습니다.")).finally(()=>setBusy(false));
      }}>대사 원본 근거 보기</button>
    </>}
    {evidence && <pre className="staking-evidence">{evidence}</pre>}
    <div className="staking-actions">{runs.map(r=><button className="ghost-button" key={r.id} disabled={busy} onClick={()=>{setSelectedId(r.id);setEvidence(null);}}>#{r.id} · Epoch {r.accountingEpoch} · {r.status}</button>)}</div>
  </section>;
}
