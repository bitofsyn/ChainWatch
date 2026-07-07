# ChainWatch 블록체인 도메인 로드맵

작성: 2026-07-08, blockchain agent. 수집(collector)·탐지(detection)·이벤트(event) 도메인의 현재 상태, 이번에 구현된 finality/reorg 의미론, 그리고 거래소급(exchange-grade)으로 가기 위한 단기/장기 계획을 정리한다.

## 1. 현재 상태 (2026-07 기준)

### 수집 파이프라인

- **체인**: Ethereum mainnet 단일. provider는 RPC(web3j)/Etherscan 선택, polling/WebSocket 모드 지원.
- **수집 흐름**: `BlockCollectionService`가 저장된 `collector_state`에서 이어서 블록을 순서대로 수집 → `CollectedBlockProcessor`가 트랜잭션 dedupe(txHash unique) 후 저장 + 상태 갱신을 하나의 DB 트랜잭션으로 처리 → Kafka(`raw-blocks`/`raw-transactions`) 발행.
- **회복력**: 재시도(지수 백오프), 스로틀, WebSocket 재연결, 재시작 시 상태 기반 재개, catch-up 배치 상한(`max-blocks-per-poll`).
- **reorg 감지**: 다음 블록의 parentHash가 직전 수집 블록 해시와 불일치하면 `reorg-rewind-depth`(기본 6)만큼 되감고 정규 체인을 재수집한다.

### 탐지 파이프라인

- 룰 4종: large-transfer, exchange-flow, rapid-transfer, watchlist-activity. 각 룰은 이제 **버전과 구조화 evidence**(임계값·관측값·매칭 주소·창 카운트)를 남긴다 (`detection_events.rule_version`, `evidence`).
- SYNC(수집 트랜잭션 내 동기 탐지) / KAFKA(raw-transactions consumer가 탐지, Detection Server 분리 대비) 2개 모드.
- 하류: detected-events 토픽 → 알림(risk score 필터 + Redis dedup), 피드 캐시, AI 분석(수동 트리거).

## 2. Finality / Reorg 의미론 (이번 구현)

### 확정(confirmation) 판정 방식

- 수집기가 `collectUpTo(head)`를 돌 때마다 관측한 체인 head를 `collector_state.last_known_chain_head`에 기록한다(**단조 증가** — 지연 도착한 낮은 head가 확정 판정을 되돌리지 못한다).
- 확정 여부는 저장하지 않고 조회 시 계산한다: `confirmations = head - blockNumber + 1`, `confirmed = confirmations >= confirmation-depth`(기본 12). `ChainFinalityService`가 담당하며 트랜잭션 API(`confirmations`/`confirmed` 필드)로 노출된다.
- **계산 방식을 택한 이유**: 플래그를 행에 박제하면 head 전진마다 UPDATE 배치가 필요하고, reorg 재수집 시 플래그 정합성이 깨질 수 있다. 계산 방식은 마이그레이션/백필이 없고 rewind와 자동으로 정합된다.

### reorg rewind와의 상호작용

- `confirmation-depth`(12) > `reorg-rewind-depth`(6)을 유지하는 것이 운영 원칙이다. rewind가 되감는 최대 구간은 head 기준 7블록 이내이므로, `confirmed=true`(12+ confirmations)인 데이터는 rewind가 도달하지 않는다.
- rewind는 **수집 진행도만** 되돌린다. head 관측치는 유지되므로 되감긴 구간은 재수집 전까지도 "미확정"으로 올바르게 판정된다.
- **고아(orphaned) 트랜잭션의 현재 한계**: rewind 후 재수집은 txHash로 dedupe되므로 중복 저장은 없지만, 정규 체인에서 사라진(uncle 블록에만 있던) 트랜잭션 행을 능동적으로 삭제/마킹하지는 않는다. 소비자는 `confirmed=false` 데이터를 잠정 데이터로 취급해야 한다. 삭제/ORPHANED 마킹은 단기 로드맵 항목.

### 멱등성(idempotency) 계층 — 검토 결과

| 계층 | 메커니즘 | 경합 시 동작 |
|---|---|---|
| 트랜잭션 저장 | `transactions.tx_hash` unique + 저장 전 exists 필터 | 위반 시 블록 트랜잭션 롤백 → 상태 미전진 → 다음 폴링이 같은 지점부터 재시도 후 수렴 |
| Kafka 탐지 consumer | `findByTxHash().orElseGet(save)` + DefaultErrorHandler(1초×3회 재시도 → DLT) | 제약 위반으로 1회차 실패해도 재시도에서 exists로 수렴. poison message는 DLT 격리 |
| 탐지 이벤트 | 애플리케이션 exists 체크 + `(transaction_id, event_type)` unique | 동시 통과 시 한쪽만 커밋, 재처리 시 exists로 수렴 |
| 알림 | Redis dedup(TTL 30분, 장애 시 fail-open) | 하류 중복 메시지 억제 |

이번에 보강한 것: 폴링 스케줄러/WebSocket 수집 스레드가 `CollectorException`만 잡던 것을 `RuntimeException`으로 확대 — DB 제약 위반(멀티 인스턴스 중복 수집 경합) 등도 에러 메트릭에 남고 다음 사이클이 정상 재개된다.

**알려진 한계 (수용된 트레이드오프)**: `DetectionService`는 이벤트 저장 직후 같은 DB 트랜잭션 안에서 Kafka 발행을 한다. 이후 다른 룰의 저장 실패로 롤백되면 이미 발행된 메시지가 "유령 이벤트"가 될 수 있고, 재처리 시 중복 발행될 수 있다. 알림 dedup이 사용자 영향은 흡수하지만, 정확히-한-번에 가깝게 가려면 after-commit 발행(또는 outbox)이 필요하다 — 단기 로드맵 항목.

## 3. 단기 로드맵 (1–2 스프린트)

1. **입출금(deposit/withdrawal) 추적**
   - 거래소 지갑 주소 집합 대비 방향성 있는 자금 흐름 모델: `deposit`(외부→거래소), `withdrawal`(거래소→외부) 뷰.
   - 현재 exchange-flow 룰의 INBOUND/OUTBOUND evidence가 기초 데이터. 지갑별 순유입/유출 집계 API(`/api/wallets/{address}/flows`)로 확장.
   - 확정 깊이와 결합: **미확정 입금은 잔고 반영 보류** 시그널로 노출 (거래소 운영의 핵심 요구).

2. **주소 라벨 / 제재(sanctions) enrichment 인터페이스**
   - `AddressLabelProvider` 인터페이스 (주소 → {label, category, source, riskHint}). 초기 구현 2개: 설정 기반(현 watchlist/exchange 주소의 일반화), 정적 파일(OFAC 등 공개 목록 스냅샷).
   - watchlist evidence의 `watchlistReason`을 provider가 준 사유로 세분화. 외부 유료 API(Chainalysis 등)는 어댑터로 뒤에 붙인다.

3. **hot/cold 지갑 태그**
   - 거래소 주소를 단순 목록에서 `{address, exchange, walletType: HOT|COLD|DEPOSIT}` 구조로 승격 (설정 스키마 additive 확장).
   - cold→외부 출금 같은 고위험 패턴 룰 추가가 목적. exchange-flow evidence에 `walletType` 포함.

4. **정합성 마무리**
   - 탐지 이벤트 Kafka 발행을 after-commit으로 이동(유령 이벤트 제거).
   - reorg rewind 시 되감긴 구간의 트랜잭션/이벤트에 ORPHAN 후보 마킹(재수집에서 재등장하지 않으면 확정 ORPHANED).
   - 룰 버전을 `/api/detection/rules` 응답에도 노출.

## 4. 장기 로드맵

1. **멀티체인 — 1단계: EVM L2 (Arbitrum, Optimism, Base)**
   - 현 구조는 이미 체인당 `collector_state` 행(collectorName 키)과 `BlockchainClient` 추상화를 갖고 있어 확장 지점이 명확하다.
   - 필요한 일반화: `network` 컬럼의 전 도메인 전파(detection_events에는 현재 없음), 체인별 CollectorProperties(폴링 주기·확정 깊이는 체인마다 다름 — L2는 L1 확정과 이중 판정), 체인별 Kafka 토픽 or 메시지 network 필드 기반 라우팅.
   - finality 의미론 교체 가능화: PoS L1은 `finalized` 태그 RPC로 depth 휴리스틱을 대체 가능. `ChainFinalityService`를 체인별 전략으로 분리.

2. **멀티체인 — 2단계: non-EVM (Solana, Bitcoin, Tron)**
   - 트랜잭션 모델이 달라(계정 vs UTXO, 다중 instruction) 현재의 단일 `transactions` 스키마로는 수용 불가. 체인 중립 코어(금액·주소·시각·확정)와 체인별 확장 테이블로 분리하는 스키마 개편이 선행 조건.
   - 탐지 룰은 체인 중립 코어 위에서 동작하도록 유지해 룰 재작성을 피한다.

3. **토큰 전송 (ERC-20/721 로그 수집)**
   - 현재는 네이티브 ETH value만 본다. 거래소급이 되려면 Transfer 이벤트 로그 수집이 필수 — 수집기에 receipt/log 파이프라인 추가, 토큰 메타데이터(decimals, symbol) 캐시, 토큰 단위 임계값 룰.

4. **탐지 고도화**
   - 룰 버전 이력 관리(버전별 발화 통계로 임계값 튜닝 근거 확보 — evidence의 ruleVersion이 기초 데이터).
   - 그래프 기반 자금 추적(다단계 경유 흐름), 피어 그룹 이상치 탐지. AI 분석 서버와 evidence 스키마 공유.

5. **운영 스케일**
   - Detection Server 분리 배포(KAFKA 모드 전제는 이미 충족), 파티션 키 전략(현 txHash → 지갑 주소 기반으로 rapid-transfer 순서 보장), outbox 패턴, 수집기 다중 인스턴스 리더 선출.

## 5. 관련 문서

- API 계약: `docs/WAVE2_BLOCKCHAIN_CONTRACTS.md`
- 운영: `docs/OPERATIONS.md`, `docs/RUNBOOKS.md`
- Wave 1 계약(권한/워크플로/감사): `docs/WAVE1_BACKEND_CONTRACTS.md`
