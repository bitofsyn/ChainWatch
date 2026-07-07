# ChainWatch 장애 대응 Runbook

이 문서는 ChainWatch 운영 중 발생 가능한 장애 시나리오별 대응 절차다. 각 항목은 증상 → 1차 확인 → 완화 →
복구 → 에스컬레이션 순으로 기술한다. 메트릭/로그 위치는 실제 코드/설정 기준으로 검증한 값만 기재했다
(추측 금지). 관련 문서: [OPERATIONS.md](./OPERATIONS.md), [LOCAL_INFRA_SETUP.md](./LOCAL_INFRA_SETUP.md),
[DEPLOYMENT_CHECKLIST.md](./DEPLOYMENT_CHECKLIST.md).

## 공통 확인 위치

- Prometheus: `http://localhost:9090` (`/alerts`, `/targets`)
- Grafana: `http://localhost:3000` (대시보드 `ChainWatch Overview`, uid `chainwatch-overview`)
- 파이프라인 상태 REST API: `GET /api/ops/pipeline` (DB/Redis/Kafka/AI서버/수집기/탐지/알림 요약,
  `backend/src/main/java/com/chainwatch/backend/ops/api/PipelineStatusController.java`)
- 수집기 상태: `GET /api/collector/state` (`lastCollectedBlock`)
- 백엔드 헬스: `GET /actuator/health`, 메트릭: `GET /actuator/prometheus`
- 로그 포맷: `시간 레벨 [스레드] 로거 - 메시지` (`backend/src/main/resources/logback-spring.xml`),
  구조화 메시지는 `key=value` 스타일 (예: `notification sent | channel=slack eventId=1 riskScore=90`)
- 컨테이너 로그: `docker compose logs -f <service>` (서비스명: `backend`, `postgres`, `redis`, `kafka`,
  `ai-analysis`, `frontend`, `kafka-ui`, `prometheus`, `grafana`)

---

## 1. RPC 프로바이더 장애 (Ethereum RPC 응답 없음/타임아웃)

**증상**
- 신규 블록 수집이 멈추고 `GET /api/collector/state`의 `lastCollectedBlock`이 갱신되지 않음
- 로그에 RPC 타임아웃/커넥션 에러 반복 (`chainwatch.ethereum.request-timeout-seconds: 30`,
  `backend/src/main/resources/application.yml`)
- `chainwatch_collector_errors_total`, `chainwatch_collector_retries_total` 증가

**1차 확인**
- 메트릭: `chainwatch_collector_rpc_latency_seconds` (p50/p95/p99, `CollectorMetrics.rpcLatency`)의 급증 또는
  스크레이프 중단
- `increase(chainwatch_collector_blocks_collected_total[15m])`가 0에 근접하는지 (수집기가
  `chainwatch.collector.enabled=true`인 상태에서)
- `GET /api/ops/pipeline`의 `collector` 컴포넌트 상세 문자열(`provider=rpc, mode=..., 마지막 수집 블록=...`)
- 재시도 정책: `chainwatch.collector.retry` (max-attempts 4, initial-delay 500ms, multiplier 2.0,
  max-delay 15000ms) — 재시도가 소진되면 `chainwatch_collector_errors_total`만 증가하고 다음 폴링
  주기(`poll-interval-ms: 15000`)에 다시 시도

**완화**
- 대체 RPC 엔드포인트가 있다면 `ETHEREUM_RPC_HTTP_URL`/`ETHEREUM_RPC_WS_URL` 환경변수를 교체 후
  백엔드 재시작 (기본값: `https://ethereum-rpc.publicnode.com`)
- Etherscan 기반 수집으로 임시 전환 가능: `CHAINWATCH_COLLECTOR_PROVIDER=etherscan` +
  `CHAINWATCH_ETHERSCAN_API_KEY` 설정 후 재시작
- 장기화 시 수집기를 일시 정지(섹션 8 참고)하여 에러 로그/재시도 폭주를 줄임

**복구**
- RPC 정상화 확인 후 별도 조치 불필요 — polling 모드는 마지막 수집 블록부터 자동 재개
  (`BlockCollectionService.lastCollectedBlockNumber()` 기준)
- websocket 모드는 `chainwatch.collector.websocket-reconnect` 정책(최대 10회, 최대 지연 60s)에 따라
  자동 재연결 시도. 재연결 소진 시 `chainwatch_collector_websocket_reconnects_total` 확인 후 수동 재시작

**에스컬레이션**
- 30분 이상 수집 중단 지속 시 블록체인 담당자 에스컬레이션. RPC 프로바이더 상태 페이지 확인
  (publicnode 등 외부 상태) 및 유상 RPC(Alchemy/Infura 등) 전환 검토

---

## 2. Kafka 장애

**증상**
- `GET /api/ops/pipeline`의 `kafka` 컴포넌트가 down (`AdminClient.describeCluster` 실패,
  `PipelineStatusController.checkKafka()`)
- 백엔드 로그에 Kafka producer/consumer 연결 에러
- 수집기가 살아있어도 raw-transactions/detected-events 토픽에 메시지가 쌓이지 않음
  (`chainwatch_collector_kafka_raw_transactions_published_total` 정체)

**1차 확인**
- 컨테이너 상태: `docker compose ps kafka`, `docker compose logs kafka`
- 브로커 리스너 구성: 내부 `PLAINTEXT://kafka:9092`, 호스트 `PLAINTEXT_HOST://localhost:9094`
  (`docker-compose.yml` — 내부/외부 포트가 다르므로 로컬 CLI 접속 시 반드시 `9094` 사용,
  컨테이너 간 통신은 `9092` 사용. 이 둘을 혼동하지 않도록 주의)
- Kafka UI: `http://localhost:8081` 에서 토픽/컨슈머 그룹 지연 확인
- 헬스체크: `kafka-topics.sh --bootstrap-server localhost:9092 --list` (컨테이너 내부 기준,
  `docker-compose.yml` healthcheck와 동일)

**완화**
- 브로커 프로세스 재시작: `docker compose restart kafka` (KRaft 단일 노드 구성이라 재시작 중 전체
  파이프라인 정지됨 — 운영 알림 필요)
- 백엔드는 Kafka 재연결을 Spring Kafka 기본 재시도로 처리하나, 장시간 단절 시 백엔드도 재시작 권장

**복구**
- Kafka 재기동 후 `docker compose ps`로 healthy 확인 → 백엔드가 자동 재연결
- 컨슈머 오프셋은 유지되므로(`auto-offset-reset: earliest`, 신규 컨슈머 그룹 한정) 재처리 유실 없음.
  단, 컨트롤러/브로커 데이터 볼륨(`kafka` 컨테이너는 별도 명명 볼륨 없이 컨테이너 내부 저장 —
  컨테이너 삭제 시 토픽 데이터 유실 가능. 운영 환경에서는 MSK 등 관리형 서비스로 대체 권장, 아래
  DEPLOYMENT_CHECKLIST.md 참고)

**에스컬레이션**
- 단일 브로커 구성이므로 브로커 자체 장애는 즉시 P1. 운영 환경에서는 MSK 멀티 AZ로 전환 필요
  (현재 로컬/단일 노드 compose 구성의 알려진 한계)

---

## 3. Postgres 장애

**증상**
- `GET /api/ops/pipeline`의 `database` 컴포넌트 down (`detectionEventRepository.count()` 실패,
  `PipelineStatusController.checkDatabase()`)
- `/actuator/health`의 `db` 컴포넌트 DOWN
- 이벤트/트랜잭션 조회 API 500 에러, HikariCP 커넥션 관련 로그

**1차 확인**
- `docker compose ps postgres`, `docker compose logs postgres`
- healthcheck: `pg_isready -U chainwatch -d chainwatch` (`docker-compose.yml`)
- HikariCP 메트릭(`/actuator/prometheus`에서 `hikaricp_connections_active`,
  `hikaricp_connections_pending` 등 Spring Boot 기본 노출)으로 커넥션 풀 고갈 여부 확인

**완화**
- 컨테이너 재시작: `docker compose restart postgres` (재시작 중 모든 쓰기 작업 실패 — 수집/탐지
  파이프라인은 재시도 후 실패 누적, Kafka 컨슈머는 커밋 실패로 메시지 재처리됨)
- 디스크 여유 공간 확인 (`postgres-data` 볼륨) — 공간 부족이 흔한 원인

**복구**
- `postgres-data` 명명 볼륨에 데이터가 영속되므로 컨테이너 재시작만으로 데이터 보존
- 스키마는 Hibernate `ddl-auto: update`로 자동 관리됨(운영 환경 리스크는
  DEPLOYMENT_CHECKLIST.md의 DB 마이그레이션 섹션 참고) — 별도 마이그레이션 스크립트 실행 불필요
- 장기 다운타임 후 복구 시 수집기가 중단된 구간의 블록을 놓쳤을 수 있으므로
  `POST /api/collector/blocks/{blockNumber}`로 누락 구간 수동 재수집 검토

**백업/복원**
- 정기 백업 절차가 코드/compose에 아직 구성되어 있지 않음 (요청 필요: `pg_dump` 기반 정기 백업
  cron 또는 RDS 자동 스냅샷 — DEPLOYMENT_CHECKLIST.md 참고)
- 수동 백업 예시: `docker exec chainwatch-postgres pg_dump -U chainwatch chainwatch > backup.sql`
- 복원 예시: `docker exec -i chainwatch-postgres psql -U chainwatch chainwatch < backup.sql`

**에스컬레이션**
- 데이터 손상/디스크 손실 의심 시 즉시 DBA/인프라 담당자 에스컬레이션. 트랜잭션/이벤트 무결성이
  핵심 제품 가치이므로 P1로 취급

---

## 4. Redis 장애 (dedupe fail-open 영향 포함)

**증상**
- `GET /api/ops/pipeline`의 `redis` 컴포넌트 down (`stringRedisTemplate.execute(PING)` 실패,
  `PipelineStatusController.checkRedis()`)
- 최근 트랜잭션/이벤트 피드(`GET /api/feed/recent-transactions`, `/api/feed/recent-events`) 응답 비거나 실패
- 알림 로그에 `notification dedupe check failed, treating as not duplicate` 경고
  (`RedisNotificationDeduplicator.isDuplicate()`)

**중요: fail-open 동작**
- `chainwatch.notification.dedup-store=redis`(기본값)일 때 Redis 장애가 발생하면
  `RedisNotificationDeduplicator`는 예외를 잡아 **중복이 아닌 것으로 간주**하고 알림 발송을 막지 않는다
  (`backend/src/main/java/com/chainwatch/backend/notification/service/RedisNotificationDeduplicator.java`).
  즉 **Redis 장애 중에는 동일 위험 이벤트에 대해 Slack/Discord로 중복 알림이 다수 발송될 수 있다.**
  이는 "알림 누락"보다 "알림 폭주"를 택한 의도된 설계(알림이 아예 끊기는 것보다 중복이 낫다는 판단)이므로,
  Redis 장애 중에는 운영자가 알림 채널에서 중복 폭주를 인지하고 있어야 한다.

**1차 확인**
- `docker compose ps redis`, `docker compose logs redis`
- healthcheck: `redis-cli ping` (`docker-compose.yml`)
- 알림 중복 발생 여부: `chainwatch_notifications_sent_total{channel,result}` (per-hour 증가율 급등 여부),
  Grafana `Notifications by Result` 패널

**완화**
- 컨테이너 재시작: `docker compose restart redis`
- Redis 장애가 길어질 것으로 예상되면, 알림 폭주를 줄이기 위해 임시로
  `chainwatch.notification.enabled=false`(환경변수로 재기동) 고려 — 단, 이 경우 해당 구간의 고위험
  이벤트 알림 자체가 완전히 끊기므로 트레이드오프를 명확히 판단할 것
- 단일 인스턴스 환경이면 `CHAINWATCH_NOTIFICATION_DEDUP_STORE=memory`로 임시 전환 가능(재시작 필요,
  인스턴스 재시작 시 dedupe 상태 초기화됨)

**복구**
- `redis-data` 명명 볼륨에 dedupe 키(`chainwatch:notification:dedupe:*`)와 최근 피드 캐시
  (`chainwatch:feed:transactions`, `chainwatch:feed:events`)가 영속되므로 컨테이너 재시작 시 보존
- 다만 Redis 컨테이너가 완전히 재생성(볼륨 삭제)된 경우 dedupe/피드 캐시는 소실 — 피드는 Kafka
  컨슈머가 재구독 시 재생성되지만, dedupe 이력 소실 직후에는 최근 발송된 알림이 다시 중복 발송될 수 있음

**에스컬레이션**
- 알림 중복 폭주가 운영 채널(Slack/Discord)에 큰 노이즈를 유발하면 알림 채널 담당자에게 사전 공지
  후 위 완화 조치 적용

---

## 5. AI 분석 프로바이더 장애/폴백

**증상**
- `GET /api/ops/pipeline`의 `aiServer` 컴포넌트 down (`ai-analysis`의 `/health` 응답 없음,
  `PipelineStatusController.checkAiServer()`)
- `chainwatch_ai_analysis_total{status="FAILED"}` 증가
- Prometheus 알림 `AiAnalysisFailures` 발화 (`increase(...[15m]) > 3`,
  `infra/monitoring/prometheus-rules.yml`)

**1차 확인**
- `docker compose logs ai-analysis`
- ai-analysis 컨테이너 헬스체크: `GET /health` (Dockerfile HEALTHCHECK)
- 백엔드 쪽 AI 클라이언트 설정: `chainwatch.ai.enabled`, `chainwatch.ai.base-url`
  (`backend/src/main/resources/application.yml`)
- AI 서버 자체 프로바이더 폴백 체인: `CHAINWATCH_AI_FALLBACK_CHAIN` (기본
  `claude,gemini,lmstudio,hermes,mock`, `ai/analysis-server/app/config.py`) — 개별 프로바이더 실패 시
  다음 프로바이더로 자동 폴백하고 최종적으로 `mock`까지 내려갈 수 있음
- **알려진 관측 공백**: ai-analysis 서버는 자체 `/metrics`(Prometheus) 엔드포인트를 노출하지 않으며
  Prometheus 스크레이프 대상에도 포함되어 있지 않다(`infra/monitoring/prometheus.yml` 확인 결과).
  현재 AI 실패는 오직 백엔드의 `chainwatch_ai_analysis_total{status}` 카운터로만 간접 관측되며,
  "어떤 프로바이더가 실패했는지", "폴백까지 걸린 지연시간"은 노출되지 않음 (요청 필요)

**완화**
- 특정 프로바이더 키 만료/쿼터 초과 시 해당 키를 무효화하고 폴백 체인의 다음 프로바이더가 자동
  사용되는지 확인 (별도 재시작 불필요, `.env` 변경 시에는 ai-analysis 컨테이너 재시작 필요)
- 전체 프로바이더 장애 시에도 `mock` 프로바이더가 항상 사용 가능하므로 서비스 자체는 완전히
  끊기지 않음 — 다만 실제 위험 분석 품질은 저하됨을 운영자에게 공지

**복구**
- 프로바이더 정상화 확인 후 `chainwatch_ai_analysis_total{status="SUCCESS"}` 회복 확인
- 실패했던 개별 분석은 `POST /api/events/{eventId}/analysis`로 수동 재요청 가능

**에스컬레이션**
- 15분 내 3회 초과 실패가 지속되면(Prometheus 알림 기준) AI 담당자 에스컬레이션. 유료 API 키
  쿼터/과금 문제인지 우선 확인

---

## 6. 웹훅/알림 발송 실패

**증상**
- `chainwatch_notifications_sent_total{result="failure"}` 증가
- Prometheus 알림 `NotificationFailures` 발화 (`increase(...[10m]) > 0`,
  `infra/monitoring/prometheus-rules.yml`)
- Slack/Discord 채널에 알림이 도착하지 않음

**1차 확인**
- 웹훅 URL 설정 여부: `GET /api/ops/pipeline`의 `notification` 컴포넌트 상세(`Slack 연결됨/미설정`,
  `Discord 연결됨/미설정`)
- 알림 발송 이력 API: `GET /api/notifications/history` (OPERATIONS.md 언급 기준)
- 백엔드 로그의 `notification sent | channel=... result=...` 구조화 로그에서 실패 사유 확인

**완화**
- Slack/Discord 웹훅 URL이 회전(rotate)되었거나 워크스페이스에서 앱이 제거된 경우가 흔한 원인 —
  `CHAINWATCH_SLACK_WEBHOOK_URL`/`CHAINWATCH_DISCORD_WEBHOOK_URL` 재발급 후 백엔드 재시작
- 웹훅 엔드포인트가 일시적 5xx를 반환하는 경우 백엔드의 아웃바운드 HTTP 클라이언트 타임아웃 설정
  (`spring.http.client.connect-timeout: 5s`, `read-timeout: 10s`)이 실패를 빠르게 표면화하므로 재시도는
  다음 이벤트 발생 시 자연히 이루어짐(즉시 재전송 큐는 없음 — 실패한 개별 알림은 재전송되지 않는다는
  점에 유의)

**복구**
- 웹훅 URL 정상화 후 신규 이벤트부터 정상 발송 확인. 장애 구간에 발생한 고위험 이벤트는
  `GET /api/events?status=NEW&riskLevel=HIGH` 등으로 조회해 운영자가 수동으로 확인 필요(실패한
  알림에 대한 자동 재전송 메커니즘은 없음)

**에스컬레이션**
- 웹훅 URL에 시크릿이 포함되므로(URL 자체가 비밀) 유출 의심 시 즉시 재발급하고 보안 담당자에게
  공유. 로그에는 URL 전체가 남지 않도록 유지할 것(현재 구조화 로그는 channel/eventId/riskScore만
  기록하며 URL을 로그에 남기지 않음 — 유지 확인됨)

---

## 7. DLT(Dead Letter Topic) 재처리 절차

**배경**
- `raw-transactions` 토픽 소비 중 재시도가 소진된 메시지는 `<raw-transactions 토픽명>.DLT`로 격리된다
  (`RawTransactionDltMonitor`, `backend/src/main/java/com/chainwatch/backend/messaging/consumer/RawTransactionDltMonitor.java`)
- 실제 토픽명은 `chainwatch.kafka.topics.raw-transactions` 설정값 기준 —
  기본값 `chainwatch.raw-transactions` (`application.yml`) → DLT 토픽은
  `chainwatch.raw-transactions.DLT`

**증상**
- `chainwatch_detection_dlt_messages_total` 증가 (0보다 크면 이상 상황)
- 백엔드 로그에 `[ERROR] Raw transaction {txHash} (block {blockNumber}) moved to DLT: {exceptionMessage}`

**1차 확인**
- Kafka UI(`http://localhost:8081`)에서 `chainwatch.raw-transactions.DLT` 토픽의 메시지 목록/오프셋 확인
- 로그에서 실패 원인이 된 예외 메시지(`exceptionMessage`) 확인 — poison message(역직렬화 실패,
  스키마 불일치 등)인지, 일시적 다운스트림 장애인지 구분

**재처리 절차**
1. DLT 메시지의 원인이 일시적 장애(예: 당시 DB/Redis 다운)였다면, 메시지 내용은 정상이므로 재처리
   가능. 원인이 poison message(파싱 불가능한 데이터 자체 결함)라면 재처리해도 다시 실패하므로
   코드 수정이 선행되어야 함.
2. Kafka UI 또는 `kafka-console-consumer`/`kafka-console-producer`로 DLT 토픽의 메시지를 읽어 원본
   페이로드를 확인한다.
3. 원인이 해소되었다면, 해당 메시지를 원본 토픽(`chainwatch.raw-transactions`)으로 재발행한다
   (Kafka UI의 메시지 재발행 기능 또는 `kafka-console-producer`로 동일 페이로드를 원본 토픽에 프로듀스).
4. 재발행 후 `chainwatch_collector_transactions_collected_total` / 탐지 이벤트 생성 여부로 정상
   처리를 확인한다.
5. 현재 코드에는 DLT 자동 재처리 API/스케줄러가 없다 (요청 필요: `POST /api/admin/dlt/replay` 같은
   관리자 전용 재처리 엔드포인트) — 지금은 위 수동 절차로만 재처리 가능하다는 점을 운영자가 인지해야 함.

**에스컬레이션**
- DLT 메시지가 지속적으로 쌓이면(poison message 반복) 블록체인/백엔드 담당자에게 즉시 에스컬레이션 —
  탐지 파이프라인 데이터 누락으로 이어질 수 있는 P1 사안

---

## 8. 수집기 일시정지/재개

**배경**
- 수집기 활성화 여부는 `chainwatch.collector.enabled` (환경변수 `CHAINWATCH_COLLECTOR_ENABLED`)로
  제어되며, 기본값은 `false`(로컬 기본), compose에서는 `SPRING_PROFILES_ACTIVE=local` 사용 시
  `application-local.yml`의 `chainwatch.collector.enabled: false`가 적용된다.
- 수동 트리거 API는 항상 사용 가능: `POST /api/collector/blocks/latest`,
  `POST /api/collector/blocks/{blockNumber}` (`CollectorController`)

**일시정지 절차**
1. 자동 폴링/웹소켓 수집을 멈추려면 `CHAINWATCH_COLLECTOR_ENABLED=false`로 설정 후 백엔드 재시작
   (현재 코드에는 런타임 토글 API가 없다 — 요청 필요: `POST /api/admin/collector/pause`처럼
   재시작 없이 켜고 끌 수 있는 관리자 API)
2. `GET /api/ops/pipeline`의 `collector` 컴포넌트가 `자동 수집 비활성 (수동 트리거만 가능)`으로
   표시되는지 확인

**재개 절차**
1. `CHAINWATCH_COLLECTOR_ENABLED=true`로 설정 후 백엔드 재시작
2. `GET /api/collector/state`로 `lastCollectedBlock`이 증가하는지 확인
3. 정지 기간 동안 누락된 블록 구간이 있다면 `POST /api/collector/blocks/{blockNumber}`로 구간별 수동
   수집 수행 (배치 백필 API는 없음 — 블록 번호를 순회하며 개별 호출 필요, 요청 필요:
   범위 지정 백필 API)

**주의**
- 수집기를 재시작 없이 끄고 켜는 기능이 없으므로, 일시정지/재개 모두 백엔드 컨테이너 재시작을
  수반한다. 재시작 중에는 API 서버 전체가 잠시 중단되므로 운영 영향(다운타임)을 감안해야 한다.

---

## 9. Reorg(체인 재구성) 급증

**배경**
- `chainwatch.collector.reorg-rewind-depth: 6` — parentHash 불일치 감지 시 6블록을 되감아 재수집
- reorg 발생 시 `chainwatch_collector_reorgs_total` 카운터 증가 (`CollectorMetrics.incrementReorg()`)

**증상**
- `increase(chainwatch_collector_reorgs_total[1h])`가 평소보다 비정상적으로 높음
- 동일 블록 구간에 대해 탐지 이벤트/트랜잭션이 재수집되며 중복 처리 부하 증가

**1차 확인**
- Grafana/Prometheus에서 `chainwatch_collector_reorgs_total`의 증가율 확인 (현재 Grafana
  Overview 대시보드에는 이 패널이 없음 — 요청 필요: reorg 카운트 패널 추가)
- 로그에서 되감기 대상 블록 범위 확인
- 이더리움 네트워크 자체의 이상(체인 스플릿, 대규모 reorg 이벤트) 여부를 공개 익스플로러로 교차 확인

**완화/복구**
- reorg-rewind-depth(6블록)가 실제 reorg 깊이보다 얕으면 일부 트랜잭션이 finality 없이 이미 이벤트로
  처리되었을 수 있음 — 이 경우 해당 구간 이벤트를 재검토하고 필요 시 오탐(false positive) 처리
- 재구성 빈도가 비정상적으로 높으면(예: RPC 프로바이더가 불안정한 노드를 가리키는 경우) RPC
  엔드포인트 교체 검토(섹션 1 참고)
- 현재 코드는 finality(확정성) 개념 없이 즉시 이벤트를 확정 처리한다 — 대규모 reorg 중에는 이미
  알림이 발송된 이벤트가 사후적으로 무효화될 수 있음(자동 취소 메커니즘 없음, 요청 필요:
  confirmation depth 기반 지연 확정 로직)

**에스컬레이션**
- 짧은 시간 내 reorg가 반복되면(6블록 되감기 범위를 벗어나는 딥 reorg 의심 시) 블록체인 담당자에게
  즉시 에스컬레이션. 딥 reorg는 중복 알림/오탐 이벤트를 대량 발생시킬 수 있음

---

## 부록: 알려진 관측/자동화 공백 (요청 필요)

아래 항목은 이번 점검에서 실제 코드/설정에 존재하지 않음을 확인한 것들이며, 만들어내지 않고
"요청 필요"로 명시한다.

- 수집기 lag(체인 head 대비 지연 블록 수) Prometheus 게이지 — 현재는 `lastCollectedBlock`이
  REST API로만 노출되고 Prometheus 메트릭이 아님
- Postgres/Redis/Kafka 개별 다운 여부를 나타내는 Prometheus 메트릭 — 현재는 `/api/ops/pipeline`
  REST 폴링으로만 확인 가능(Prometheus 스크레이프 대상 아님)
- AI 프로바이더별(claude/gemini/lmstudio/hermes) 성공/실패/지연 세분화 메트릭, ai-analysis 서버
  자체의 `/metrics` 엔드포인트
- 고위험 이벤트 미확인(unacknowledged) 경과 시간 게이지
- DLT 자동/반자동 재처리 API, 수집기 런타임 pause/resume API, 범위 지정 백필 API
- Postgres 정기 백업 자동화(cron/스냅샷)
