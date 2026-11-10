export function DemoGuidePage() {
  return <>
    <section className="page-head"><div><p className="eyebrow">CHAINWATCH · V1</p>
      <h1>검증인 기능 처음 사용하기</h1>
      <p className="page-lede">검증인의 현재 상태를 가져오고, 잔액 변화가 보상·패널티·출금 내역과 맞는지 확인하는 방법을 안내합니다.</p>
    </div></section>
    <section className="panel staking-panel"><h2>이 기능은 무엇을 하나요?</h2>
      <p><strong>검증인</strong>은 이더리움에 ETH를 예치하고 블록 제안과 투표에 참여하는 운영 주체입니다. ChainWatch는 선택한 검증인의 상태와 잔액을 정기적으로 저장할 수 있습니다.</p>
      <p><strong>잔액 변화 검산</strong>은 두 시점의 잔액 차이가 보상, 패널티, 출금 내역으로 설명되는지 비교하는 작업입니다. 자금을 이동하거나 보상을 지급하지 않습니다.</p>
    </section>
    <section className="panel staking-panel"><h2>비활성 상태에서 시작하기</h2>
      <p>기본 설치에서는 외부 노드에 임의로 연결하지 않도록 이 기능이 꺼져 있습니다. 운영자는 프로젝트 루트의 <code>.env</code>에 다음 값을 추가해야 합니다.</p>
      <pre className="staking-evidence">{`CHAINWATCH_STAKING_ENABLED=true
CHAINWATCH_STAKING_NETWORK=설정할-네트워크-이름
CHAINWATCH_BEACON_URL=http://Beacon-API-주소
CHAINWATCH_BEACON_GENESIS_ROOT=0x로-시작하는-genesis-root
CHAINWATCH_STAKING_VALIDATORS=42,43

# 잔액 변화 검산도 사용할 때
CHAINWATCH_STAKING_RECONCILIATION_ENABLED=true
CHAINWATCH_STAKING_RECONCILIATION_EXECUTION_URL=http://Execution-RPC-주소`}</pre>
      <p>설정 후 <code>docker compose --profile app up -d --force-recreate backend</code>로 백엔드를 다시 시작합니다. Beacon API는 로컬에서 실행한 Teku·Lighthouse 등의 합의 클라이언트나 신뢰할 수 있는 제공자의 주소를 사용해야 합니다.</p>
    </section>
    <section className="panel staking-panel"><h2>사용 순서</h2>
      <ol>
        <li><a href="#/staking">검증인 상태</a> 화면에서 설정한 네트워크와 검증인 번호를 확인합니다.</li>
        <li>관리자로 로그인하고 <strong>최신 상태 가져오기</strong>를 눌러 상태와 잔액을 저장합니다.</li>
        <li>확정된 Epoch 번호를 입력하고 <strong>잔액 변화 확인</strong>을 누릅니다.</li>
        <li>결과가 일치하는지, 설명되지 않은 차이가 있는지 확인합니다.</li>
        <li>필요하면 원본 데이터에서 Beacon API와 execution RPC 응답을 확인합니다.</li>
      </ol>
    </section>
    <section className="panel staking-panel"><h2>결과를 읽는 방법</h2>
      <p><strong>계산과 잔액이 일치함</strong>: 시작 잔액 + 보상 − 패널티 − 출금 = 종료 잔액이며 설명되지 않은 차이가 0입니다.</p>
      <p><strong>설명되지 않는 차이가 있음</strong>: 자료는 모았지만 계산과 실제 잔액 사이에 차이가 있습니다.</p>
      <p><strong>자료가 부족해 확인 보류</strong>: 미확정 구간, 노드 응답 실패 또는 누락 때문에 판단하지 못했습니다.</p>
      <p>금액은 모두 정수 gwei입니다. 입력 epoch는 잔액 반영 구간이며, 증명 보상의 참여 epoch와는 다릅니다.</p>
    </section>
    <section className="panel staking-panel"><h2>검증 범위</h2>
      <p>Geth·Teku 로컬 minimal 개발망의 Electra/Fulu를 대상으로 합니다. 자료 간 대사 결과이며 독립적인 합의·서명 검증은 아닙니다.</p>
      <p>노드 프로세스 일시 정지 → timeout 기록 → 재개 → 같은 구간 재대사 순서로 장애 대응 흐름을 확인할 수 있습니다. 검증인 재시작이나 자동 복구를 증명하는 실험은 아닙니다.</p>
      <p>고객 자금 수탁, 지분 배분, 원장, 지급, 공개 테스트넷 장기 운영은 v1 범위에 포함하지 않습니다.</p>
    </section>
  </>;
}
