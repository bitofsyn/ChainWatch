# Wave 2 Blockchain API Contracts (확정 깊이 / 룰 Evidence)

작성: 2026-07-08, blockchain agent. 프론트엔드 에이전트가 참조하는 계약 문서. 전부 additive 변경 — 기존 필드/의미는 그대로다.

## 1. 트랜잭션 확정(confirmation) 필드

`GET /api/transactions` (content 요소), `GET /api/transactions/{id}` 응답에 필드 2개 추가:

```json
{
  "...기존 필드...": "id, txHash, fromAddress, toAddress, amount, gasFee, blockNumber, timestamp, contractAddress",
  "confirmations": 18,
  "confirmed": true
}
```

- `confirmations` (number|null): 마지막으로 관측한 체인 head 기준 `head - blockNumber + 1`. head 블록에 포함된 직후가 1.
- `confirmed` (boolean|null): `confirmations >= chainwatch.collector.confirmation-depth`(기본 12)이면 true.
- **둘 다 null인 경우**: 수집기가 아직 체인 head를 관측하지 못한 상태(수집 이전, 레거시 collector_state). UI는 "확정 여부 알 수 없음"으로 처리할 것. `confirmed=false`(미확정)와 구분해야 한다.
- 미확정(`confirmed=false`) 데이터는 reorg 시 되감기(rewind) 대상 범위에 있을 수 있다. confirmation-depth(12) > reorg-rewind-depth(6)이므로 `confirmed=true` 데이터는 rewind가 도달하지 않는 구간이다.

## 2. 탐지 이벤트 evidence / ruleVersion

`GET /api/events/{id}` (상세 응답에만) 필드 2개 추가. 목록(`GET /api/events`)에는 추가하지 않았다.

```json
{
  "...기존 필드...": "id, eventType, riskLevel, riskScore, summary, walletAddress, txHash, detectedAt, transactionId, status, assignee, statusChangedAt, resolutionReason, falsePositiveReason, notes, aiReport",
  "ruleVersion": "1.0",
  "evidence": { "rule": "large-transfer", "ruleVersion": "1.0", "...룰별 필드...": "아래 참조" }
}
```

- `ruleVersion` (string|null): 발화한 룰의 버전. 이번 배포 이전에 생성된 레거시 이벤트는 null.
- `evidence` (object|null): 룰이 발화한 이유의 구조화 JSON. 레거시 이벤트/직렬화 실패 시 null. 공통 키는 `rule`, `ruleVersion`이고 나머지는 룰별 스키마.

### evidence 스키마 — eventType별 예시

**LARGE_TRANSFER** (rule: `large-transfer`)

```json
{
  "rule": "large-transfer",
  "ruleVersion": "1.0",
  "thresholdEth": 100.0,
  "observedAmountEth": 250,
  "fromAddress": "0xwhale...",
  "toAddress": "0xreceiver..."
}
```

**EXCHANGE_FLOW** (rule: `exchange-flow`)

```json
{
  "rule": "exchange-flow",
  "ruleVersion": "1.0",
  "thresholdEth": 50.0,
  "observedAmountEth": 80,
  "direction": "INBOUND",
  "matchedExchangeAddress": "0xexchange...",
  "counterpartyAddress": "0xuser..."
}
```

- `direction`: `INBOUND`(거래소로 입금) | `OUTBOUND`(거래소에서 출금)

**RAPID_TRANSFER** (rule: `rapid-transfer`)

```json
{
  "rule": "rapid-transfer",
  "ruleVersion": "1.0",
  "windowMinutes": 10,
  "thresholdCount": 3,
  "observedTransferCount": 5,
  "windowStart": "2026-07-08T11:50:00Z",
  "fromAddress": "0xburst..."
}
```

- `windowStart` (ISO-8601 string): 카운트 집계 창의 시작 시각 (트랜잭션 timestamp - windowMinutes).

**WHALE_ACTIVITY** (rule: `watchlist-activity`)

```json
{
  "rule": "watchlist-activity",
  "ruleVersion": "1.0",
  "matchedAddress": "0xwatched...",
  "matchedDirection": "FROM",
  "watchlistReason": "configured-watchlist-address",
  "counterpartyAddress": "0xother..."
}
```

- `matchedDirection`: `FROM`(watchlist 지갑이 송신) | `TO`(수신)
- `watchlistReason`: 현재는 고정값 `configured-watchlist-address` (설정 기반 watchlist). 라벨/제재 데이터 연동 시 세분화 예정 — 프론트는 문자열 그대로 표기하면 된다.

## 3. Kafka 메시지 — 변경 없음

`chainwatch.detected-events`(`DetectedEventMessage`), `chainwatch.raw-transactions`/`raw-blocks` 스키마는 그대로다. evidence는 DB/REST에서만 노출된다.

## 4. 운영 API

`GET /api/ops/pipeline`의 collector 컴포넌트 `detail` 문자열에 `확정 기준 N confirmations` 문구가 추가되었다 (자유 텍스트, 파싱하지 말 것).

## 5. DB 변경 (additive만, JPA ddl-auto=update)

- `transactions`: 변경 없음 (확정 여부는 저장하지 않고 head - blockNumber로 매번 계산)
- `detection_events`: `rule_version` varchar(20), `evidence` text — 둘 다 nullable 신규 컬럼
- `collector_state`: `last_known_chain_head` bigint nullable 신규 컬럼 (관측한 체인 head, 단조 증가)
- 기존 유니크 제약/인덱스 변경 없음

## 6. 설정 키 추가

- `chainwatch.collector.confirmation-depth` (기본 12): 확정 판정 기준 confirmations. reorg-rewind-depth(6)보다 크게 유지할 것.
