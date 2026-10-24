# ChainWatch v3: 스테이킹 운영·정산 검증 서비스

작성일: 2026-10-22
상태: 관측·대사 v1 마무리. 아래 원장·풀 설계는 후속 구상이며 v1 완료 기준은 13절, 실제 구현은 11~12절에 기록한다.
이 문서가 서비스 정의·아키텍처·구현 순서의 단일 기준이다. 이전 입금 설계에서 필요한 공통 원칙을 아래에 통합했다.

## 1. 지원 목표와 판단 근거

1차 목표: Staking Backend Engineer. Java/Spring 서비스 구현, 외부 시스템 연동, 데이터 정합성, 운영 장애 분석 경험을 활용한다.
2차 목표: Research Engineer. DataGather 소스 분석·문서화 경험을 특정 블록체인 클라이언트의 코드 분석과 재현 실험으로 확장한다.

2026-10-22 사용자가 확인한 채용 현황(주니어 블록체인 모집 없음, 스테이킹·리서치 경력무관)을 지원 전략의 입력으로 사용한다. 공식 채용 페이지는 조회 시 403으로 직접 확인되지 않았다. 과거 검색 결과로 현재 채용 중임을 단정하지 않는다. 제출 시 공식 공고의 상태와 요건을 다시 확인한다.
경력무관은 전문 지식 요구가 낮다는 의미가 아니다. 두 직무를 같은 깊이로 모두 충족한다고 주장하지 않는다. 첨부 공고의 프로토콜·검증인 이해, 코어 소스 분석 요건을 별도 증거로 준비한다.

## 2. 서비스 정의

ChainWatch는 테스트 환경의 거래소 스테이킹 운영자가 검증인 상태와 자산 변동을 추적하고, 고객별 모의 지분·보상·반환 기록의 일치 여부를 검증하며, 장애 원인을 조사하는 백엔드 서비스다.

중심 문제: 온체인 검증인의 상태와 자산 변화가 고객별 내부 기록에 정확히 연결됐는지, 연결이 중단됐을 때 무엇을 근거로 복구할지 설명한다.
사용자는 운영자와 데모 고객이다. 운영자는 활성화·지연·보상·반환·불일치를 확인한다. 데모 고객은 자신의 신청, 배정 지분, 보상과 해지 진행 상태를 조회한다.

완료 기준은 기능 화면 완성이 아니라 다음 다섯 결과물의 결합이다.
1. 특정 실패를 재현하는 자동화 실험.
2. 원인과 설계 선택, 대안·단점을 기록한 설계 개선.
3. 테스트 체인에 연결된 배포 서비스와 검증인 운영 증거.
4. 같은 입력에서 재현 가능한 정합성·성능 검증 보고서.
5. 프로토콜 또는 클라이언트 소스를 근거로 한 기술 분석.

## 3. 범위와 구현 순서

Ethereum 네이티브 스테이킹 한 가지를 다룬다. ERC-20·유동성 스테이킹·멀티체인·실제 고객 자금·주문 체결은 제외한다. 입금은 전용 프로덕트로 확장하지 않고 지분 배정에 필요한 검증된 테스트 자금 기록으로 제한한다.

첫 구현은 검증인 관측과 대사부터 시작한다. 고객 신청 UI나 임의 APR 보상 생성부터 만들지 않는다.
- A: execution/consensus 관측 어댑터, 검증인 수명주기·잔액·출금 기록, 운영 지표.
- B: 고정 참여자·고정 지분의 모의 풀, 보상 관측·대사·원장. 모집 마감 이후 중도 가입은 지원하지 않는다.
- C: 풀 단위 신청·활성화·전체 해지·반환 시나리오. 고객별 즉시 해지나 유동성 제공은 제외한다.
- D: 실패 재현, 복구 검증, 배포 관측 보고서와 소스 분석.

프로토콜의 검증인 상태와 서비스 주문 상태를 별도 저장한다. 고객 신청 완료는 검증인 활성화를 의미하지 않으며, 해지 요청 접수도 즉시 반환을 의미하지 않는다. 풀의 테스트 자금과 고객별 모의 지분 매핑을 명시하고 실제 거래소 고객 수탁으로 표현하지 않는다.

## 4. 목표 아키텍처

```text
테스트 체인
  Execution Client -- JSON-RPC -- Execution Adapter
  Consensus Client -- Beacon API -- Consensus Adapter
  Validator Client -- 운영 지표 -- Validator Observer
                                  |
                 검증된 체크포인트·원본 관측 저장
                                  |
          Staking Lifecycle / Reward Reconciliation
                                  |
           PostgreSQL: 상태 + 원장 + 감사 + Outbox
                   |                         |
          고객·운영 API / React        Relay → Kafka → 알림
```

Spring Boot 모듈러 모놀리스를 유지한다. PostgreSQL이 업무 상태와 원장의 기준이며 Kafka는 커밋 이후 전달, Redis는 선택적 조회 캐시다. AI·기존 이상거래 탐지는 핵심 경로에서 제외한다.

검증인·합의·실행 클라이언트는 Spring 애플리케이션과 별도 프로세스로 운영한다. Spring은 합의나 서명을 재구현하지 않는다. 검증인 키는 운영 클라이언트에만 제공하고 일반 API·DB·프론트에 저장하지 않는다. 초기 활성화·종료 명령은 인증된 별도 운영 절차로 수행하고 결과를 서비스가 추적한다. 자동 명령 API는 후속 범위다.

주요 모듈:
- observation: execution block hash, consensus state/root, epoch, validator id, 조회 시각·client version 보존.
- lifecycle: 신청·풀 배정과 온체인 상태 매핑, 지연·관측 불명확 상태 구분.
- reconciliation: 입출금·검증인 잔액 변화·보상·패널티·수수료 수익을 구분하여 근거를 대조.
- ledger: 최소 단위 정수 금액, 균형 분개, 불변 기록, 중복 settlement 차단.
- operations: 작업 이력, 관측 재수집, 정산 중지·재개, 사용자 소유권·역할 검증.

DB 원자성·Outbox·불변 원장 원칙을 적용한다. 입금용 txHash 키를 epoch 정산 키로 그대로 사용하지 않는다.

## 5. 정산과 불변식

보상은 고정 APR로 생성하지 않는다. 검증인 잔액 차이를 그대로 보상으로 간주하지 않는다. 예치·반환·패널티가 잔액에 영향을 주기 때문이다.

동일한 확정 기준의 구간에서 다음 관계를 검증한다.
기말 검증인 잔액 = 기초 잔액 + 예치 유입 - 출금 + consensus 보상 - 패널티.
Execution fee recipient 수익은 별도 자산 흐름으로 추적한다. 출금은 원금과 이미 인식한 보상의 이동일 수 있으므로 수익으로 재계상하지 않는다. MEV 연동은 첫 범위에서 제외하고 미분류 수익은 별도 계정으로 격리한다.

계산식의 각 항목을 선택한 fork·client의 API와 소스로 확인하고, 확인되지 않은 차이는 임의 보상으로 메우지 않는다. 관측이 불완전하면 정산을 보류한다. 실행·합의 데이터를 같은 기준으로 연결할 수 없으면 INCOMPLETE 상태다.

고정 풀의 참여 지분과 배분 규칙은 활성화 전에 동결한다. 수수료는 MVP에서 0으로 두며 원금·보상·패널티 배분 규칙을 버전 관리한다. 최소 단위 나눗셈의 잔여분은 풀 잔여 계정에 기록하고 풀 종료 시 결정적인 고객 ID 순서로 배분한다. 배분 가능한 보상을 초과해 지급하지 않는다. 활성화 중 지분 양도·추가 가입은 제외한다.

불변식:
- 정산 business key(network, pool, period, settlement_kind)는 유일하다. 관측 root는 근거로 별도 저장해 root 변경이 중복 지급을 만들지 않게 한다.
- 고객 배분 합계 + 잔여분 = 해당 구간의 배분 대상 금액.
- 원장 차변 합계 = 대변 합계, 잔고 투영 = 원장 계산값.
- 패널티와 원금 손실도 기록하며 양의 보상만 골라 계산하지 않는다.
- 재조회·worker 중복·프로세스 재시작이 고객의 경제적 효과를 중복 생성하지 않는다.
- 확정된 근거의 모순을 발견하면 정산을 중지하고 사건을 생성한다. 과거 분개를 덮어쓰지 않는다.

정산·원장·outbox는 한 DB 트랜잭션에서 처리한다. RPC 호출은 잠금 밖에서 수행하고, 커밋 시 관측 revision을 재검사한다. 보상은 관측/발생과 실제 반환/가용 잔고를 별도 표시한다.

### 공통 저장·전달·권한 원칙

- 금액은 최소 단위 정수(BigInteger / NUMERIC(78,0))로 저장하고 API에서는 문자열로 전달한다. 원본 데이터의 wei/gwei 단위를 명시하고 변환을 검증한다.
- 원장 분개는 수정·삭제하지 않는다. 정정이 필요하면 원본 참조를 가진 반대 분개를 추가한다. 분개 균형은 커밋 시 검증하고, 잔고 투영은 원장으로 재계산할 수 있어야 한다.
- 관측 데이터와 수집 커서는 함께 커밋한다. 미수집·조회 실패 작업은 DB에 남겨 재시작 후 재시도한다.
- 상태 변경·분개·잔고 투영·감사 기록·outbox는 같은 DB 트랜잭션에서 처리한다. unique business key와 고정된 계정 잠금 순서로 중복·경합을 제어한다.
- Outbox relay는 발행 성공 직후 중단될 수 있다. 소비자는 event_id inbox와 자신의 변경을 함께 커밋하고, aggregate version으로 오래된 이벤트의 덮어쓰기를 방지한다. 외부 알림의 완전한 단일 전달은 보장하지 않는다.
- 고객 데이터 소유권은 ADMIN/ANALYST 역할과 별도로 검사한다. 운영 작업은 범위·요청자·사유·결과를 기록하고 검증 근거를 우회하지 않는다.
- 새 정산은 별도 테스트 DB/프로필과 버전 관리된 migration에서 시작한다. 기존 탐지 데이터를 고객 원장으로 변환하지 않는다.

## 6. 테스트·배포·운영

| 환경 | 검증 대상 | 주장할 수 없는 것 |
|---|---|---|
| 실제 PostgreSQL·Kafka 컨테이너와 고정 fixture | 동시성·원자성·중복·네트워크 지연 | 실제 합의·검증인 운영 |
| 로컬 execution + consensus + validator 개발망 | 활성화·종료·관측 복구, 통제된 장애 | 공개망의 전체 환경과 경제적 보안 |
| 공개 스테이킹 테스트넷 | 실제 클라이언트 연동·검증인 운영·공개망 지연 | 실제 고객 자산 운영·메인넷 규모 |
| 클라우드의 API·운영 UI | 배포·재시작·관측·대사·알림 | 실사용자 수요 검증 |

공개 검증인 실험 후보는 Hoodi다. Sepolia는 일반 애플리케이션 연동과 구분한다. Anvil 단독 실행은 합의 클라이언트나 검증인 운영 경험으로 기록하지 않는다.

공개 검증인 활성화에 필요한 테스트 자금, 노드 CPU/RAM/디스크, 예상 비용과 동기화 시간을 사전 PoC에서 확인한다. 자원이 확보되지 않으면 로컬 결과와 공개망 읽기 전용 관측까지 진행하되, 검증인 운영 완료로 표시하지 않는다. 유료 인프라 구매·프로비저닝은 이 문서 변경에 포함되지 않는다.

공개 환경에서는 정상 동작·재시작·관측 장애를 실험한다. 중복 서명·slashing 등 파괴적인 조건은 격리된 로컬 환경에서 검증한다. 키 인스턴스를 복제해 장애 복구하지 않으며 slashing protection 데이터의 보존·이전 절차를 확인한다.

필수 실험:
1. 정산 커밋 직전/직후 강제 종료와 재실행.
2. 동일 관측·정산 작업의 동시 전달 및 중복 실행.
3. Beacon/RPC timeout, 오래된 응답, 서로 다른 checkpoint.
4. 출금이 포함된 구간에서 잔액 감소를 손실로 오분류하는 기존 계산과 개선 계산 비교.
5. 보상·패널티·반올림 잔여의 합계 검증.
6. Kafka 중단, 발행 성공 후 relay 종료, 복구 시 중복 소비.
7. 관측 프로세스 중단 후 공백 재수집과 대사.
8. 검증인 재시작과 slashing protection 유지 확인.
9. 체인 근거 변경·정산 근거 모순 시 중지 및 사건 생성.

목표 운영 기간은 최소 14일이다. 목표와 실제 달성 기간을 구분하고 정상 실행·중단·재배포·장애 시간을 모두 기록한다. 활성화/종료 큐가 관측 기간 내 완료되지 않으면 그대로 미완료로 보고한다.

성능 실험은 공개 RPC에 부하를 가하지 않고 로컬 fixture/replay로 수행한다. 입력 건수, validator 수, epoch 범위, 중복 비율, 서버 사양, 동시성, 버전을 고정한다. p50/p95/p99, 오류율, backlog, DB lock 대기, 복구 시간을 측정한다. 목표 TPS는 기준 측정 후 결정한다.

## 7. Research 산출물

주제 후보: Java 기반 합의 클라이언트 Teku의 slashing protection 저장·복구 경로. Java 소스 분석 경험과 연결하되 버전·commit을 고정하고 실제 코드를 읽은 뒤 구체적 함수 경로를 기록한다.

보고서 구조:
- 연구 질문과 관련 프로토콜 규칙.
- 소스 경로·호출 흐름·저장 경계, 명세와 구현의 연결.
- 가설, 재현 환경, 실패 입력, 기대 결과, 실제 결과.
- 백엔드 연동 또는 운영에 미치는 영향과 적용 조건.
- 반례·미검증 범위·남은 위험.

문서 요약만으로 코어 분석 완료라 하지 않는다. 알려진 방어 동작의 재현과 새로운 취약점 발견을 구분한다. Research 지원본은 이 보고서를 앞에, Staking 지원본은 서비스·운영·정산 결과를 앞에 배치한다. 하나의 보고서가 Research 공고 전체 요건을 대체하지는 않는다.

## 8. 단계별 완료 게이트

1. 타당성 PoC: 실행/합의 데이터 접근, 테스트넷 자금·노드 자원, client version, 표본 거래·검증인 상태를 확인한다. 기술 선택 이유를 기록한다.
2. 관측·대사: 원본과 checkpoint를 보존하고 정상/불완전/불일치를 구별한다. 이 단계부터 검증인 관측 운영을 시작한다.
3. 모의 풀·원장: 정상 배분·패널티·중복·재실행에서 불변식이 유지된다.
4. 장애 재현·개선: 개선 전 실패와 개선 후 통과를 같은 입력으로 재현한다. 채택하지 않은 대안과 한계를 문서화한다.
5. 배포 운영: 실제 기간과 환경을 명시한 로그·대사·장애·복구 보고서를 만든다.
6. 제출 패키지: 아키텍처, 재현 스크립트, 테스트 결과, 측정 보고서, 소스 분석, 짧은 데모, 미완료 항목을 함께 제공한다.

서비스 UI만 완성하거나 고정 수익률을 모의 계산한 상태는 완료가 아니다. 운영 증거 없이 노드 운영 경험을 주장하지 않는다. 실무 경력(엑셈/한국전력)과 개인 프로젝트를 분리해 기술한다.

## 9. 기존 프로젝트 활용

재사용 후보: Collector의 재개 패턴, 인증·감사, 지표·알림, React 운영 화면, Kafka 기반 전달.
신규 핵심: Beacon 관측 어댑터, 검증인 상태 모델, 풀·정산·원장, 보상 대사, 클라이언트 운영 및 연구 실험.
보조 기능: 기존 이상거래 탐지·AI 리포트. 이 기능의 확장보다 신규 핵심 검증을 우선한다.
완전한 고객 입금 서비스를 먼저 끝내지 않는다. 지분·원장에 필요한 최소 기반만 가져온다. 기존 메인넷 데이터는 새 테스트 정산으로 백필하지 않는다.

## 10. 참고 및 확인 상태

- 사용자 제공 Staking/Research 공고 이미지: 직무 비교의 주요 근거.
- Research 공식 링크(접근 제한): https://career.bithumbcorp.com/ko/o/230005
- Ethereum networks: https://ethereum.org/developers/docs/networks/ — Hoodi의 검증인·스테이킹 테스트 용도.
- Teku slashing protection: https://docs.teku.consensys.io/26.7.0/concepts/slashing-protection — 보호 기록과 이전 절차 참고. 실제 실험 버전은 PoC에서 별도 고정.

## 11. 구현 현황과 로컬 재현 (2026-10-22)

### 완료 범위: Beacon 관측의 첫 수직 기능

- 설정으로 지정한 최대 20개 검증인만 조회한다. 호출자가 임의 RPC 주소를 전달할 수 없다.
- genesis validators root를 기대값과 대조하고 client version을 기록한다.
- finalized state root를 먼저 확정한 뒤 그 root를 명시한 validator API만 조회한다. 낙관적 실행/미확정/필수 필드 누락/검증인 불일치/잘못된 uint64를 거부한다.
- 잔액은 gwei 정수 문자열로 보존한다. 잔액을 보상으로 환산하거나 원장에 반영하지 않는다.
- HTTP 호출 전에 RUNNING 시도를 DB에 커밋한다. 검증된 응답은 OBSERVED, 예상 가능한 네트워크·응답 오류는 INCOMPLETE로 기록한다. 성공 원본 응답과 시각·클라이언트 버전을 보존한다.
- API: GET/POST `/api/ops/staking/observations`, GET `/api/ops/staking/observations/{id}/evidence`.
- JWT 활성 모드에서 POST는 ADMIN만 가능하고 조회는 ADMIN/ANALYST가 가능하다. 실행·결과를 기존 감사 로그에 기록한다.
- `#/staking`에서 최근 실패와 마지막 성공을 별도 표시하고 원본 근거를 조회한다.

### 로컬 실행

저장소 루트에서 fixture를 실행한다. fixture는 실제 Beacon 노드가 아니다.

```bash
python3 scripts/staking/beacon_fixture.py --scenario-file /tmp/chainwatch-staking-scenario
```

별도 터미널의 `backend` 디렉터리에서:

```bash
./gradlew bootRun --args='--spring.profiles.active=staking-poc'
```

이 프로필은 `127.0.0.1:19596` fixture, 서버 포트 18090, 인메모리 H2를 사용한다. JWT 비활성 개발 모드이므로 외부 공개용으로 사용하지 않는다. 관리자 버튼을 사용하려면 기본 개발 계정으로 로그인한다. 서버 재시작 시 H2 데이터는 사라지므로 이 프로필로 재시작 영속성 검증을 주장하지 않는다.

별도 터미널의 `frontend` 디렉터리에서:

```bash
API_PROXY_TARGET=http://localhost:18090 npm run dev
```

출력된 프론트 주소의 `#/staking`을 연다. 저장소 루트에서 재현 검증:

```bash
python3 scripts/staking/smoke_observation.py
```

스크립트는 fixture 시나리오를 정상 → 503 장애 → 정상으로 바꾸고 실제 API와 저장 이력을 검증한 뒤 시나리오 파일을 복원한다. 외부 RPC를 호출하지 않는다.

### 검증 결과와 한계

- Beacon client/API 자동 테스트 15개 통과: root 고정, uint64 보존, 네트워크·검증인 불일치, 미확정·optimistic 거부, 404·timeout, 권한, 실패 이력, 오래된 관측과 원본 조회.
- 실행 API smoke: OBSERVED → INCOMPLETE(BEACON_HTTP_503) → OBSERVED, 모든 시도 이력 유지 확인.
- 백엔드 전체 회귀 테스트 190개 통과(실패·오류·스킵 0).
- 프론트 빌드와 기존 테스트 120개 통과. 브라우저에서 실제 Teku 관측의 검증인 3개·정수 잔액·원본 버튼·이력을 확인했다.
- PostgreSQL 16에서 정상·503·복구 3회 이력 저장과 Spring 재시작 후 영속성을 확인했다. Kafka 연동 및 PostgreSQL 동시성 부하 검증은 별도 미수행이다.
- 관측은 현재 수동 실행이다. 인스턴스 내부 중복 실행만 막으며 다중 인스턴스 분산 잠금은 없다. 프로세스 중단 후 남은 RUNNING은 완료로 표시하지 않으며 자동 복구 작업은 후속이다.
- stale은 조회 시각과 원본 체인 시각을 모두 고려한다. genesis time + slot × SECONDS_PER_SLOT로 계산하며 미래 30초 초과 및 기본 30분 이상 오래된 checkpoint를 거부한다. fork/client 전체 호환성 검증은 미완료다.
- 현재 성공 시 원본 근거를 보존하지만 실패 중 부분 응답은 보존하지 않는다. 정산 입력으로 사용하기 전 epoch/slot 및 execution 데이터 연결 검증을 추가해야 한다.
- PostgreSQL 신규 테이블 SQL은 `backend/src/main/resources/db/staking/V001__staking_observations.sql`에 있다. 자동 migration runner는 아직 없으며, 기존 Hibernate schema update 방식과 혼동하지 않는다. V002는 block root·slot·epoch·state time을 추가한다. 로컬 PostgreSQL에서 두 migration 적용을 확인했다.
- 풀·보상 대사·원장·Outbox·장기 검증인 운영·14일 운영 보고서·Teku 소스 분석은 미구현/미수행이다.

후속 구현 결과: execution 연결과 epoch 대사는 12절에 기록했다. 모의 원장은 13절의 v1 범위 밖으로 남긴다. 관측 잔액 차이 자체를 보상으로 간주하지 않는다.


### PostgreSQL 및 실제 로컬 개발망

```bash
docker compose -f compose.staking-lab.yml up -d
# Kurtosis CLI 1.22.2 사용. 공식 release checksum으로 확인한 CLI를 PATH에 추가한다.
kurtosis run --enclave chainwatch-staking-paired github.com/ethpandaops/ethereum-package@3ad03186953bfa27e9060860aa9d636ea496f010 --args-file scripts/staking/devnet.yaml
kurtosis port print chainwatch-staking-paired cl-1-teku-geth http
# 위 출력 URL을 사용한다. 기존 18090 백엔드를 먼저 종료한다.
python3 scripts/staking/run_devnet_backend.py --beacon-url http://127.0.0.1:<출력된포트>
```

`devnet.yaml`은 Geth + Teku 2개 피어, 각 128개 테스트 검증인, minimal preset(6초 slot, 8 slots/epoch)이며 사용한 클라이언트 이미지 digest를 고정한다. genesis root는 로컬 개발망에서만 발견하여 설정한다. 외부 네트워크에서는 신뢰할 수 있는 경로로 기대 genesis root를 별도 지정해야 한다. 자동 생성 테스트 키는 실제 자산에 사용하지 않는다.

PostgreSQL은 loopback 55433 및 전용 volume을 사용한다. initdb migration은 빈 volume에서만 자동 실행되므로 기존 volume의 추가 migration은 별도 적용해야 한다. 개발 프로필의 JWT 비활성 설정은 공개 배포용이 아니다.

단일 Teku 피어 구성은 peer 대기로 head slot 0에서 멈췄다. 2개 피어 구성으로 변경한 뒤 블록 생성 진행을 확인했다. `wait_for_finalization` 옵션의 실행 완료만으로 확정 진행을 판단하지 않고 Beacon header를 직접 검사한다. 초기 genesis도 API에서 finalized=true이므로 slot > 0 및 후속 slot 증가를 함께 확인해야 한다.

로컬 개발망 중지: `kurtosis enclave stop chainwatch-staking-paired`. PostgreSQL 중지: `docker compose -f compose.staking-lab.yml stop`. 데이터 volume은 삭제하지 않는다. 이번 단일 피어 실험 enclave `chainwatch-staking-devnet`은 중지 상태로 보존했다.

재현 검증: 개발망과 백엔드를 실행한 뒤 `python3 scripts/staking/smoke_devnet.py`. 최대 10분 동안 genesis 이후 서로 다른 두 finalized slot의 증가, root 변경, 검증인 상태 및 원본 일치를 검사한다. 정상 응답이어도 slot이 진행되지 않으면 실패한다.

2026-10-22 실제 개발망 smoke 통과: 관측 #6(slot 16, epoch 2) → #10(slot 24, epoch 3). 서로 다른 state root, 검증인 0·1·2의 active_ongoing 상태, 원본 header와 저장 결과의 일치 및 이력 보존을 확인했다. 클라이언트는 `teku/v26.10.1+32-g77aa8f474b`였다. 이것은 로컬 통합 검증이며 공개 테스트넷 운영·보상 정확성 검증을 의미하지 않는다.

## 12. 확정 블록 연결 및 epoch 대사 (2026-10-23)

### 구현한 범위

`staking/reconciliation` 모듈은 **로컬 minimal preset, Electra/Fulu, 최대 4,096개 검증인 상태**에서 설정된 검증인만 대사한다. 공개망에서 큰 debug state를 다운로드하지 않도록 preset을 먼저 검사한다. 이 단계의 MATCHED는 관측 자료 간 일치를 뜻하며 지급 승인이나 원장 반영이 아니다. `settlementEligible`은 항상 false다.

입력 accounting epoch E의 구간은 **slot E×S 처리 후 상태 → slot (E+1)×S 처리 후 상태**이며 S는 노드 spec의 SLOTS_PER_EPOCH다. 따라서 블록 효과는 `(시작 slot, 종료 slot]`, 증명 보상은 `E-1` epoch다. `process_rewards_and_penalties`가 E→E+1 전환에서 previous_epoch(E-1)를 사용하는 점을 반영했다. “참여 epoch”와 “잔액 반영 구간”을 혼동하지 않는다.

검증 순서:
1. genesis root·genesis 시각·fork·체인 ID와 확정된 종료 구간 확인.
2. 경계 header의 state root로 full state를 고정 조회. 경계 slot이 없는 경우 추정하지 않고 보류.
3. 구간의 Beacon block parent 연결과 header 필드 대조.
4. 각 execution payload를 Geth `eth_getBlockByHash` 및 `eth_getBlockByNumber`와 대조. hash·number·parent·timestamp·fee recipient·withdrawals 확인.
5. attestation reward의 head/target/source/inactivity, proposer reward, sync committee reward를 부호 있는 정수 gwei로 집계. 동기화 위원회 구성과 응답 검증인 목록을 비교해 누락을 0으로 해석하지 않음.
6. `기초 + consensus 보상 - consensus 패널티 - 출금 = 기말` 검증. 보상·패널티 합계를 별도 보존하고 미설명 차액은 residual로 표시.
7. 조회 후 경계 root를 다시 확인하고 결과·원본 근거·감사 로그를 저장.

`MATCHED`는 모든 잔액의 residual=0, `MISMATCH`는 검증 가능한 입력의 미설명 차액, `INCOMPLETE`는 자료 부족·미지원 흐름이다. 호출 전에 RUNNING을 별도 커밋하고 실패 시에도 수집한 부분 원본을 보존한다. 같은 epoch 재실행은 새로운 관측 시도를 만들지만 경제적 효과는 만들지 않는다. 요청당 응답 4 MB, 총 원본 24 MB, 전체 RPC 수집 3분 및 개별 호출 timeout을 제한한다.

### 의도적으로 보류하는 경우

- 빠진 slot/과거 상태 또는 보상 API 조회 불가, 미확정·optimistic 응답.
- pending deposit/consolidation, 구간 내 deposit·slashing·execution request, 관측 대상 slashed/exit 상태, 경계 fork 전환.
- execution/consensus 불일치, 잘못된 정수·누락/중복 보상 응답.

예치 유입을 0으로 가정해 보상을 맞추지 않는다. 지원 범위 밖이면 INCOMPLETE다. Execution fee recipient의 수수료/MEV 수익은 합의 검증인 잔액에 포함하지 않으며 별도 수익 대사는 후속이다. 노드가 반환한 구조를 대조하는 구현으로, SSZ root·서명·상태 전이의 독립 검증기는 아니다. full state를 쓰는 현재 접근은 공개망용 확장 설계가 아니다.

### API 및 실행

- GET `/api/ops/staking/reconciliations`: 활성 여부와 최근 20개 시도.
- POST 같은 경로, JSON `{"epoch": 1}`: 대사 실행. JWT 활성 환경에서는 ADMIN만 가능.
- GET `/api/ops/staking/reconciliations/{id}/evidence`: 현재 network/genesis에 속한 시도의 원본 근거.
- `#/staking`에 대사 실행·결과·이력·원본 조회 패널 추가. 모든 금액은 gwei 문자열로 표시.

기존 PostgreSQL volume에는 V003을 별도 적용한다:

```bash
docker compose -f compose.staking-lab.yml exec -T postgres psql -U staking_lab -d staking_lab < backend/src/main/resources/db/staking/V003__staking_reconciliations.sql
kurtosis port print chainwatch-reconciliation cl-1-teku-geth http
kurtosis port print chainwatch-reconciliation el-1-geth-teku rpc
python3 scripts/staking/run_devnet_backend.py --beacon-url http://127.0.0.1:<Beacon포트> --execution-url http://127.0.0.1:<RPC포트>
python3 scripts/staking/smoke_reconciliation.py --epoch 1
```

실제 enclave 이름에 맞게 바꾼다. `--execution-url`을 생략하면 대사는 비활성화된다. devnet launcher는 `local-teku-<genesis_time>`을 network 이름으로 사용한다. 같은 테스트 키를 재사용하면 서로 다른 개발망도 genesis validators root가 같을 수 있으므로 시각을 추가해 이력을 구분한다. 프로덕션 네트워크 식별 정책으로 일반화하지 않는다.

### 확인한 실제 결과

2026-10-23 로컬 Geth + Teku 및 PostgreSQL에서 epoch 1(slot 8→16)의 execution 블록 9개를 연결 검증했다. 검증인 0은 +413,659 gwei, 1·2는 각각 -447,200 gwei였고, 모두 residual=0이었다. 동일 구간 반복 실행 결과가 같고 미래 epoch는 EPOCH_NOT_FINALIZED로 기록됐다. 정상·실패 원본 조회 및 서버 재시작 후 시도 #6·#7·#8 이력 유지도 확인했다.

재부팅 뒤 기존 검증인 keystore lock 때문에 노드가 기동하지 않는 문제가 발생하여 기존 `chainwatch-staking-paired`를 중지·보존하고 별도 `chainwatch-reconciliation` 개발망으로 검증했다. 개인키/잠금 보호를 우회해 기존 검증인을 동시에 기동하지 않았다.

참고 명세:
- [Attestation rewards API](https://github.com/ethereum/beacon-APIs/blob/master/apis/beacon/rewards/attestations.yaml)
- [Block rewards API](https://github.com/ethereum/beacon-APIs/blob/master/apis/beacon/rewards/blocks.yaml)
- [Sync committee rewards API](https://github.com/ethereum/beacon-APIs/blob/master/apis/beacon/rewards/sync_committee.yaml)
- [Altair reward/penalty processing](https://github.com/ethereum/consensus-specs/blob/master/specs/altair/beacon-chain.md#rewards-and-penalties)

후속 확장 후보는 제한된 대사 결과를 고정 모의 풀 지분과 연결하는 원장 설계이며 v1 종료에 필요한 작업은 아니다. 분산 작업 잠금·중단 RUNNING 자동 복구·고객별 배분·원장·Outbox·공개 테스트넷 운영은 아직 완료되지 않았다.

검증 기록: 백엔드 전체 회귀 203개 통과 후 마지막 genesis/fork·정수 입력 검증 변경에 대해 대사 테스트 15개를 추가 실행하여 통과했다. 프론트 빌드와 기존 120개 테스트 통과. 브라우저에서 관리자 로그인 → epoch 1 실행 → MATCHED 및 이력 조회를 확인했다. 자동 테스트에는 보상과 음수 패널티·출금 합산, 차액 1 gwei 불일치, 실행 hash 불일치, 누락 블록/보상, pending 예치, 미확정, 잘못된 체인 및 권한·입력 오류가 포함된다.


## 13. 관측·대사 v1 범위 확정

사용자와 합의해 모의 풀·원장 확장을 미루고 **검증인 관측·보상 대사 서비스**로 v1을 마무리한다. 2~8절의 고객별 지분·원장·Outbox·장기 운영·리서치 목표는 후속 설계이며 v1 구현 사실과 구분한다.

### 설계 설명: 왜 잔액 차이를 보상으로 쓰지 않는가

잔액의 증가·감소에는 증명 보상뿐 아니라 제안·동기화 보상, 패널티, 예치·출금이 섞인다. 예를 들어 잔액이 감소했더라도 그 전부가 패널티는 아닐 수 있다. 이 때문에 별도 보상 API와 블록의 withdrawal을 수집하고 계산값을 기말 잔액에 대조한다. 지원하지 않는 예치·슬래싱·consolidation이 나타나면 보상으로 차액을 채우지 않고 보류한다.

이전 chainwatch-reconciliation 개발망의 epoch 1 관측에서 검증인 0은 +413,659 gwei, 검증인 1·2는 -447,200 gwei였다. 양수만 집계하면 원금 손실을 숨기게 되므로 보상·패널티를 별도로 표시한다. 계산 residual=0은 해당 구간의 수집 자료가 서로 맞는다는 뜻이며 독립적인 consensus 검증이나 자산 안전성 보장이 아니다.

epoch의 의미도 고정해야 한다. 참여 epoch와 잔액 반영 epoch가 다르므로 이 서비스는 post-state(E×S)에서 post-state((E+1)×S)까지를 비교하고 E-1의 증명 보상을 사용한다. 블록 효과는 시작 블록을 제외하고 종료 블록을 포함한다. 경계에서 한 블록을 두 번 세거나 보상을 한 epoch 앞당기면 residual로 드러난다.

### 실패 재현과 설계 근거

`scripts/staking/smoke_outage.py`는 지정한 전용 Teku 프로세스를 Docker pause로 정지한다. RPC는 연결 후 응답을 받지 못하므로 timeout으로 끝나며 대사 시도는 INCOMPLETE로 기록된다. finally에서 unpause한 뒤 동일 epoch를 재대사하고 정상 결과·이력·원본 보존을 확인한다. 이 실험은 노드 무응답 처리 및 수동 재실행의 증거다. 컨테이너 파괴·노드 재시작·자동 복구·분산 작업 중복을 검증했다고 주장하지 않는다.

개선 전 실패란 외부 RPC 지연을 단순 오류로 버리거나 마지막 성공을 현재 상태처럼 취급하면 운영자가 최신 시도의 실패를 놓칠 수 있다는 문제다. 현재는 네트워크 호출 전 RUNNING을 DB에 커밋하고 성공/실패를 별도 시도로 보존한다. 기존 코드와 비교한 성능 개선 수치를 만들지는 않았다. 해당 패턴의 정상·장애·복구 결과를 동일한 epoch로 재현한다.

### 제출용 3분 데모

1. 0:00~0:30 — 서비스의 문제와 로컬 테스트 범위 설명. 실제 자산·지급 없음 명시.
2. 0:30~1:00 — 관측·대사에서 확정 slot, 검증인 상태와 잔액, 원본 근거 확인.
3. 1:00~1:40 — epoch 1 대사 실행, 보상/패널티/미설명 차액 설명. 기초와 기말 잔액 비교.
4. 1:40~2:10 — 같은 구간 재실행. 결과 동일, 시도 ID 별도, 경제적 효과 없음 설명.
5. 2:10~2:40 — 미확정 epoch 또는 장애 실험의 INCOMPLETE 선택. 이전 MATCHED와 실패를 구분.
6. 2:40~3:00 — 복구 결과와 원본 보존, 미지원 흐름·후속 과제 설명.

실행 안내는 README에 통합했다. 화면은 관측·대사 / 실행·데모 안내 / 관리자만 핵심 메뉴로 노출하고 기존 탐지용 실시간 티커를 제거했다. 기존 기능은 코드와 직접 경로로 남아 있으나 이번 제출 범위에 포함하지 않는다. 데모 안내는 실제 조작 순서이며 녹화 영상이 아니다.

### v1 종료 기준과 남은 범위

v1 종료 기준은 실행 가능한 코드, 재현 스크립트, 실제 실패·복구 결과, 설계 설명, 검증된 데모 흐름이다. 지원 범위가 좁다는 점은 명시적으로 문서화한다. 공개 테스트넷/클라우드 장기 운영, 원장, 자동 복구, Teku 코어 소스 분석 보고서는 완료로 표기하지 않는다. Research 지원용 코어 분석을 이 v1이 대체하지 않는다.

### v1 최종 재현 기록 — 2026-10-23

새 chainwatch-v1 개발망(local-teku-1792761354)에서 확정 slot 16 → 24 진행을 확인했다. 동일 epoch 1에 대해 #11 MATCHED → Teku pause → #12 INCOMPLETE / UPSTREAM_TIMEOUT(5.025초) → unpause → #13 MATCHED를 재현했다. 복구 전후 보고서는 동일하고 검증인 3명의 residual은 모두 0 gwei였다. [실험 결과 JSON](evidence/staking-v1-outage.json)에 실행 ID와 복구 여부를 보존했다. 시간은 해당 1회 실험의 측정값이며 성능 벤치마크가 아니다.

프론트 빌드와 테스트 120개가 통과했고, 브라우저에서 관측·대사와 실행·데모 안내를 확인했다. Compose 프로젝트 이름을 chainwatch-staking-lab으로 고정하여 기존 탐지용 PostgreSQL과 이름 충돌을 방지했다. 기존 DB 컨테이너는 원래 데이터 볼륨을 사용하는 설정으로 복원했다.
