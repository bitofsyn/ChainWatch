const eventRows = [
  {
    type: "LARGE_TRANSFER",
    wallet: "0x8f2a...19ce",
    score: 92,
    status: "critical",
    summary: "이전 활동 이력이 없는 신규 지갑으로 2,140 ETH가 이동했습니다."
  },
  {
    type: "EXCHANGE_FLOW",
    wallet: "0x71bc...2af1",
    score: 81,
    status: "high",
    summary: "12분 내 거래소 태그 지갑으로 반복 유입 패턴이 감지되었습니다."
  },
  {
    type: "RAPID_TRANSFER",
    wallet: "0xa91e...44d0",
    score: 67,
    status: "elevated",
    summary: "연결된 5개 지갑 사이에서 짧은 시간 내 연속 이체가 발생했습니다."
  }
];

const feedRows = [
  "Etherscan 소스에서 블록 #20124591 수집 완료",
  "Kafka Consumer가 최근 탐지 이벤트 3건을 Redis에 캐시함",
  "AI 분석 파이프라인은 FastAPI 엔드포인트 설정 대기 중"
];

export default function App() {
  return (
    <main className="app-shell">
      <section className="hero-panel">
        <div className="hero-copy">
          <p className="eyebrow">ChainWatch 대시보드</p>
          <h1>실시간 위험 대응을 위한 AI 기반 온체인 이상거래 모니터링</h1>
          <p className="hero-text">
            Kafka, Redis, PostgreSQL, AI 리포트 파이프라인을 기반으로 대규모 자금 이동,
            거래소 입출금 패턴, 고래 지갑 활동을 추적하는 백엔드 중심 탐지 시스템입니다.
          </p>
        </div>

        <div className="hero-metrics">
          <article className="metric-card">
            <span>탐지 이벤트</span>
            <strong>128</strong>
            <small>최근 24시간</small>
          </article>
          <article className="metric-card">
            <span>치명 위험</span>
            <strong>14</strong>
            <small>즉시 알림 필요</small>
          </article>
          <article className="metric-card">
            <span>수집 소스</span>
            <strong>Etherscan</strong>
            <small>메인넷 연결 중</small>
          </article>
        </div>
      </section>

      <section className="grid-panel">
        <article className="glass-card wide">
          <div className="section-head">
            <div>
              <p className="section-kicker">탐지 피드</p>
              <h2>우선 대응이 필요한 이상거래 목록</h2>
            </div>
            <button className="ghost-button">전체 이벤트 보기</button>
          </div>

          <div className="table-wrap">
            {eventRows.map((row) => (
              <div className="event-row" key={`${row.type}-${row.wallet}`}>
                <div>
                  <span className={`status-pill ${row.status}`}>{row.status}</span>
                  <h3>{row.type}</h3>
                </div>
                <p>{row.summary}</p>
                <div className="wallet-col">
                  <span>{row.wallet}</span>
                  <strong>{row.score}</strong>
                </div>
              </div>
            ))}
          </div>
        </article>

        <article className="glass-card">
          <div className="section-head compact">
            <div>
              <p className="section-kicker">시스템 상태</p>
              <h2>파이프라인 상태</h2>
            </div>
          </div>

          <ul className="pulse-list">
            <li>
              <span className="dot online" />
              Collector {"->"} Kafka 발행 활성화
            </li>
            <li>
              <span className="dot online" />
              Redis 피드 캐시 Consumer 동작 중
            </li>
            <li>
              <span className="dot standby" />
              AI 분석 엔드포인트 실연동 검증 대기
            </li>
          </ul>
        </article>

        <article className="glass-card">
          <div className="section-head compact">
            <div>
              <p className="section-kicker">최근 구현 메모</p>
              <h2>백엔드 진행 현황</h2>
            </div>
          </div>

          <div className="notes-feed">
            {feedRows.map((item) => (
              <div className="note-item" key={item}>
                {item}
              </div>
            ))}
          </div>
        </article>
      </section>
    </main>
  );
}
