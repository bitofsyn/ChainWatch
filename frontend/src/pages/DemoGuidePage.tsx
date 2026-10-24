export function DemoGuidePage() {
  return <>
    <section className="page-head"><div><p className="eyebrow">CHAINWATCH · V1</p>
      <h1>검증인 관측·보상 대사</h1>
      <p className="page-lede">테스트 블록체인의 검증인 잔액 변화가 보상·패널티·출금으로 설명되는지 확인하고, 판단에 사용한 원본과 실패 이력을 남깁니다.</p>
    </div></section>
    <section className="panel staking-panel"><h2>3분 데모 흐름</h2>
      <ol>
        <li><a href="#/staking">관측·대사</a>에서 네트워크, 확정 slot, 검증인 상태와 잔액을 확인합니다.</li>
        <li>관리자로 로그인한 뒤 확정된 epoch의 대사를 실행합니다. 로컬 개발망에서는 epoch 1부터 시작합니다.</li>
        <li>MATCHED 결과의 보상·패널티·미설명 차액을 확인합니다. 같은 epoch를 재실행해 결과가 같고 시도 이력은 구분되는지 봅니다.</li>
        <li>미확정 epoch를 요청해 INCOMPLETE와 오류 사유를 확인하고, 이전 MATCHED 이력을 다시 선택합니다.</li>
        <li>대사 원본 근거에서 Beacon 응답과 execution RPC 응답을 확인합니다.</li>
      </ol>
    </section>
    <section className="panel staking-panel"><h2>결과를 읽는 방법</h2>
      <p><strong>MATCHED</strong>: 지원하는 범위에서 기초 잔액 + 보상 − 패널티 − 출금 = 기말 잔액이며 미설명 차액이 0입니다.</p>
      <p><strong>MISMATCH</strong>: 자료는 모았지만 계산과 실제 잔액 사이에 설명되지 않는 차이가 있습니다.</p>
      <p><strong>INCOMPLETE</strong>: 미확정, 노드 응답 실패, 누락 또는 지원하지 않는 자산 흐름 때문에 판정을 보류했습니다.</p>
      <p>금액은 모두 정수 gwei입니다. 입력 epoch는 잔액 반영 구간이며, 증명 보상의 참여 epoch와는 다릅니다.</p>
    </section>
    <section className="panel staking-panel"><h2>검증 범위</h2>
      <p>Geth·Teku 로컬 minimal 개발망의 Electra/Fulu를 대상으로 합니다. 자료 간 대사 결과이며 독립적인 합의·서명 검증은 아닙니다.</p>
      <p>노드 프로세스 일시 정지 → timeout 기록 → 재개 → 같은 구간 재대사는 저장소의 smoke_outage.py로 재현합니다. 검증인 재시작이나 자동 복구를 증명하는 실험은 아닙니다.</p>
      <p>고객 자금 수탁, 지분 배분, 원장, 지급, 공개 테스트넷 장기 운영은 v1 범위에 포함하지 않습니다.</p>
    </section>
  </>;
}
