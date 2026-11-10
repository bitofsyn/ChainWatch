import { useEffect, useState } from "react";
import { fetchReconciliations, reconcileEpoch, fetchReconciliationEvidence, type ReconciliationRun } from "../api";
import { useAuth } from "../contexts/AuthContext";

const RESULT_LABEL: Record<string, string> = {
  MATCHED: "계산과 잔액이 일치함",
  MISMATCH: "설명되지 않는 차이가 있음",
  INCOMPLETE: "자료가 부족해 확인 보류",
  RUNNING: "확인 중",
  FAILED: "확인 실패"
};

function resultLabel(status: string) {
  return RESULT_LABEL[status] ?? status;
}

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
    .catch(() => {if(active) setError("잔액 검산 이력을 불러오지 못했습니다.");}); return () => {active=false;}; }, []);
  async function load(execute = false) {
    setBusy(true); setError(null); setEvidence(null);
    try {
      if (execute) {const result = await reconcileEpoch(Number(epoch));setSelectedId(result.id);}
      const d = await fetchReconciliations();setRuns(d.runs);setEnabled(d.enabled);
    } catch {setError("잔액 검산에 실패했습니다. 노드 연결 상태와 로그인 권한을 확인해주세요.");}
    finally {setBusy(false);}
  }
  const selected = runs.find(r => r.id === selectedId) ?? runs[0];
  return <section className="panel staking-panel">
    <h2>보상 내역과 잔액 변화 검산</h2>
    <p>선택한 기간의 시작·종료 잔액을 보상, 패널티, 출금 내역과 비교해 차이가 있는지 확인합니다. 이 기능은 계산 결과만 보여주며 실제 지급이나 원장 변경은 하지 않습니다.</p>
    <p><strong>Epoch</strong>는 이더리움에서 약 6.4분 단위의 처리 구간입니다. 확인하려는 확정 Epoch 번호를 입력하세요.</p>
    <div className="staking-actions">
      <label>확인할 Epoch <input aria-label="확인할 Epoch" type="number" min="1" max="1000000" value={epoch} onChange={e=>setEpoch(e.target.value)} /></label>
      {user?.role === "ADMIN" && <button className="ghost-button" disabled={busy || !enabled || !/^[1-9][0-9]*$/.test(epoch) || Number(epoch)>1000000} onClick={()=>void load(true)}>{busy ? "확인 중…" : "잔액 변화 확인"}</button>}
      <button className="ghost-button" disabled={busy} onClick={()=>void load()}>결과 새로고침</button>
    </div>
    {!enabled && <p>검증인 상태 조회를 먼저 설정해야 잔액 변화도 확인할 수 있습니다. 위의 <strong>설정 예시와 사용 순서 보기</strong>를 참고하세요.</p>}
    {error && <p role="alert">{error}</p>}
    {enabled && !runs.length && <p>아직 확인한 내역이 없습니다. 확정된 Epoch 번호를 입력하고 <strong>잔액 변화 확인</strong>을 누르세요.</p>}
    {selected && <>
      <p role="status">Epoch {selected.accountingEpoch}: <strong>{resultLabel(selected.status)}</strong>{selected.errorCode && ` · ${selected.errorCode}`}</p>
      <p>Slot {selected.report?.startSlot ?? "—"} → {selected.report?.endSlot ?? "—"} · 확인한 실행 블록 {selected.report?.linkedBlocks ?? "—"}개 · 실제 지급에는 반영되지 않음</p>
      {selected.report?.validators && <p>오른쪽으로 스크롤하면 잔액과 보상 구성 항목을 볼 수 있습니다. 금액 단위는 모두 gwei입니다.</p>}
      {selected.report?.validators && <div className="staking-table"><table><thead><tr>
        <th>검증인 번호</th><th>총 보상</th><th>총 패널티</th><th>설명되지 않은 차이</th><th>시작 잔액</th><th>종료 잔액</th><th>투표 참여 순액</th><th>블록 제안 보상</th><th>동기화 참여 순액</th><th>출금</th>
      </tr></thead><tbody>{selected.report.validators.map(r=><tr key={r.validatorIndex}>
        <td>{r.validatorIndex}</td><td>{r.consensusRewardsGwei}</td><td>{r.consensusPenaltiesGwei}</td><td>{r.residualGwei}</td><td>{r.openingGwei}</td><td>{r.closingGwei}</td><td>{r.attestationNetGwei}</td><td>{r.proposerRewardGwei}</td><td>{r.syncNetGwei}</td><td>{r.withdrawalsGwei}</td>
      </tr>)}</tbody></table></div>}
      <button className="ghost-button" disabled={busy} onClick={()=>{
        setBusy(true);setError(null);fetchReconciliationEvidence(selected.id).then(d=>setEvidence(JSON.stringify(d,null,2)))
          .catch(()=>setError("검산에 사용한 원본 데이터를 불러오지 못했습니다.")).finally(()=>setBusy(false));
      }}>검산에 사용한 원본 데이터 보기</button>
    </>}
    {evidence && <pre className="staking-evidence">{evidence}</pre>}
    <div className="staking-actions">{runs.map(r=><button className="ghost-button" key={r.id} disabled={busy} onClick={()=>{setSelectedId(r.id);setEvidence(null);}}>#{r.id} · Epoch {r.accountingEpoch} · {resultLabel(r.status)}</button>)}</div>
  </section>;
}
