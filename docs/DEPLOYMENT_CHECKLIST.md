# ChainWatch 배포 준비 체크리스트 (Staging/Production)

이 문서는 ChainWatch를 로컬 docker compose 환경을 넘어 staging/production 환경에 배포하기 위한
체크리스트다. 실제 크리덴셜/승인이 필요한 항목은 명시적으로 표시했다. **이 문서 작성만으로 실제
클라우드 배포를 수행하지 않았다** — 배포는 사용자의 명시적 승인과 크리덴셜 제공 이후에만 진행한다.

관련 문서: [OPERATIONS.md](./OPERATIONS.md), [RUNBOOKS.md](./RUNBOOKS.md),
[LOCAL_INFRA_SETUP.md](./LOCAL_INFRA_SETUP.md).

---

## 1. 필수 환경 변수 / 시크릿 목록

`docker-compose.yml`과 `backend/src/main/resources/application*.yml`을 실제로 읽어 정리한 목록이다.

### 1-1. 백엔드 (Spring Boot)

| 환경 변수 | 기본값 | 운영 환경 필수 여부 | 비고 |
|---|---|---|---|
| `CHAINWATCH_JWT_ENABLED` | `false` | **필수 (true)** | 프로덕션에서 비활성 상태로 두면 인증 없이 API 접근 가능 |
| `CHAINWATCH_JWT_SECRET` | 내장 개발용 값(`chainwatch-local-dev-secret-key-change-in-production-0123456789`) | **필수 교체** | `SecurityConfig`가 JWT 활성 + 기본값 그대로면 **기동 자체를 실패**시키도록 되어 있음(의도된 안전장치). `openssl rand -hex 32` 등으로 생성 |
| `CHAINWATCH_ADMIN_USERNAME` | `admin` | 필수 교체 | |
| `CHAINWATCH_ADMIN_PASSWORD` | `{noop}chainwatch` (평문 encoder) | **필수 교체** | 기본값 그대로면 경고 로그만 남고 기동은 되므로 배포 전 사람이 직접 확인해야 함. bcrypt 해시로 발급 권장(`{bcrypt}...`) |
| `CHAINWATCH_ETHERSCAN_API_KEY` | 빈 값 | provider=etherscan일 때 필수 | Etherscan 무료 API 키 (요청 필요: 실 키는 사용자가 발급/제공) |
| `ETHEREUM_RPC_HTTP_URL` | `https://ethereum-rpc.publicnode.com` | 운영 권장 교체 | 무료 퍼블릭 RPC는 레이트리밋/가용성 리스크. 유상 RPC(Alchemy/Infura 등) 권장 (요청 필요: 실 크리덴셜) |
| `ETHEREUM_RPC_WS_URL` | `wss://ethereum-rpc.publicnode.com` | websocket 모드 사용 시 | 상동 |
| `CHAINWATCH_COLLECTOR_PROVIDER` | `rpc` | 환경별 결정 | `rpc` \| `etherscan` |
| `CHAINWATCH_COLLECTOR_MODE` | `polling` | 환경별 결정 | `polling` \| `websocket` |
| `CHAINWATCH_COLLECTOR_ENABLED` | `false` | 운영 환경에서 `true` | 활성화 전 RPC/Etherscan 크리덴셜 준비 필요 |
| `CHAINWATCH_DETECTION_MODE` | `sync` | 트래픽 규모에 따라 `kafka` 고려 | |
| `CHAINWATCH_NOTIFICATION_DEDUP_STORE` | `redis` | 다중 인스턴스면 `redis` 유지 | 단일 인스턴스 한정 `memory` 가능 |
| `CHAINWATCH_SLACK_WEBHOOK_URL` | 빈 값 | 알림 사용 시 필수 | **시크릿** — 요청 필요(운영 Slack 워크스페이스에서 발급) |
| `CHAINWATCH_DISCORD_WEBHOOK_URL` | 빈 값 | 알림 사용 시 필수 | **시크릿** — 요청 필요 |
| `SPRING_DATASOURCE_URL` / `SPRING_DATA_REDIS_HOST` / `SPRING_KAFKA_BOOTSTRAP_SERVERS` | compose 기본값 | 운영 인프라 주소로 교체 | RDS/ElastiCache/MSK 엔드포인트 (요청 필요: 프로비저닝 후 실제 값) |

### 1-2. AI 분석 서버 (FastAPI, `ai/analysis-server`)

| 환경 변수 | 비고 |
|---|---|
| `CHAINWATCH_AI_DEFAULT_PROVIDER` | 기본 `claude`. compose 기본값은 `mock` (`docker-compose.yml`의 `CHAINWATCH_AI_DEFAULT_PROVIDER:-mock` — 운영 배포 시 명시적으로 실 프로바이더로 교체 필요) |
| `CHAINWATCH_AI_ANTHROPIC_API_KEY` (compose에서는 `ANTHROPIC_API_KEY`로 전달) | **시크릿, 요청 필요** |
| `CHAINWATCH_AI_GEMINI_API_KEY` (compose에서는 `GEMINI_API_KEY`) | **시크릿, 요청 필요** (선택) |
| `CHAINWATCH_AI_FALLBACK_CHAIN` | 기본 `claude,gemini,lmstudio,hermes,mock` — 운영에서 `mock`이 마지막 폴백으로 남아있는 것은 의도적(완전 정지 방지) |

### 1-3. Grafana / 인프라

| 환경 변수 | 비고 |
|---|---|
| `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` | 기본 `admin`/`chainwatch` — **필수 교체** |
| Postgres 계정(`POSTGRES_USER`/`POSTGRES_PASSWORD`) | compose에 하드코딩(`chainwatch`/`chainwatch`) — 운영에서는 RDS 마스터 계정으로 대체, compose 값 그대로 쓰지 말 것 |

**요청 필요 항목 요약**: Etherscan API 키, 유상 RPC 엔드포인트/키, Slack/Discord 웹훅 URL,
Anthropic/Gemini API 키, 운영 도메인/TLS 인증서, RDS/ElastiCache/MSK 엔드포인트 — 모두 실 크리덴셜이
필요하므로 이 문서에서는 자리표시자만 정의한다.

---

## 2. TLS / 호스트명 전략

- 현재 `frontend/nginx.conf`는 80 포트 HTTP만 처리하며 TLS 종단이 없다.
- 권장 전략: **ALB(Application Load Balancer) 또는 리버스 프록시 앞단에서 TLS 종단**, 백엔드/프론트엔드
  컨테이너는 내부 통신만 평문 HTTP 유지 (사설 서브넷 내이므로 허용 가능한 트레이드오프).
  - AWS 경로: ALB + ACM 인증서(도메인 검증 필요, 요청 필요: 소유 도메인)
  - 단일 서버 경로(EC2): Nginx 앞단에 인증서 배치 또는 Let's Encrypt(certbot) — `docs/OPERATIONS.md`의
    AWS 배포 절차에 이미 대안으로 언급됨
- 호스트명: `api.<domain>`(백엔드 직접 노출은 지양, `/api` 경로로 프론트엔드 Nginx를 통해서만 노출
  권장 — 현재 구조가 이미 이 형태), `<domain>`(프론트엔드)
- CORS: 백엔드가 별도 도메인으로 분리 배포될 경우 `SecurityConfig`의 CORS 설정을 실제 프론트엔드
  오리진으로 제한해야 함 (현재는 동일 오리진 프록시 구조라 이슈 없음 — 도메인 분리 시 재검토 필요)

---

## 3. DB 마이그레이션 전략 (중요 리스크)

**현재 상태 (코드 확인 결과)**

```yaml
# backend/src/main/resources/application.yml, application-local.yml 공통
spring:
  jpa:
    hibernate:
      ddl-auto: update
```

- Flyway/Liquibase 등 버전 관리형 마이그레이션 도구가 프로젝트에 **없다**
  (`build.gradle.kts`에 flyway/liquibase 의존성 없음 확인).
- `ddl-auto: update`는 Hibernate가 엔티티 매핑을 스캔해 **자동으로 스키마를 변경**한다.

**리스크**

1. 운영 DB에서 `ddl-auto: update`를 그대로 쓰면, 배포 시점에 백엔드 인스턴스가 여러 대 동시에 뜨는
   경우 여러 인스턴스가 동시에 스키마 변경을 시도할 수 있어 경합/실패 가능성이 있다.
2. 컬럼 삭제/타입 축소처럼 Hibernate가 자동으로 처리하지 못하거나 데이터 손실을 유발할 수 있는
   변경은 감지되지 않거나 반영되지 않는다(예: 컬럼명 변경은 삭제+추가로 처리되어 데이터 유실).
3. 롤백 시 스키마를 이전 상태로 되돌리는 절차가 없다 — 애플리케이션 롤백만으로는 스키마가 롤백되지
   않는다.
4. 실행된 DDL의 이력(누가 언제 무엇을 바꿨는지)이 남지 않아 감사 추적이 불가능하다.

**권장 조치 (구현은 백엔드 담당 범위 — 여기서는 리스크만 명시하고 인프라 관점 권고만 기술)**

- 운영 전환 전에 Flyway 또는 Liquibase 도입을 강력히 권장하며, 그 전까지는 `ddl-auto: validate`로
  전환해 애플리케이션이 예상 스키마와 실제 스키마 불일치를 감지하면 기동을 실패시키도록 하는 것이
  현재 상태보다 안전하다(단, 이 변경은 백엔드 담당 에이전트/팀의 검토가 필요하므로 이 인프라 문서에서는
  강제 적용하지 않았다).
- 마이그레이션 도구 도입 전까지 운영 배포 시 스키마 변경은 **단일 인스턴스에서 먼저 기동해 스키마를
  안정화한 뒤** 나머지 인스턴스를 순차 기동하는 방식으로 경합을 완화한다.
- 배포 전 반드시 DB 스냅샷/백업을 선행한다(RDS 자동 스냅샷 또는 수동 `pg_dump`).

---

## 4. 롤백 전략

- **애플리케이션 롤백**: 컨테이너 이미지 태그를 이전 버전으로 되돌려 재배포(ECS/EKS라면 이전 태스크
  정의 리비전으로 롤백, EC2 compose라면 이전 이미지 태그로 `docker compose --profile app up -d`).
- **스키마 롤백**: 위 3번 항목의 이유로 자동 롤백 수단이 없다. 스키마를 변경하는 배포 전에는 반드시
  DB 백업을 선행하고, 문제가 발생하면 백업에서 복원하는 것을 유일한 안전한 롤백 경로로 간주한다.
- **Kafka 토픽 호환성**: 메시지 스키마(`RawTransactionEvent` 등 Kafka 메시지 페이로드) 변경 시
  구버전 컨슈머와의 호환성을 배포 전에 확인한다. 현재 구조상 스키마 버전 필드가 없으므로(요청 필요:
  메시지 스키마 버전 필드 추가) 페이로드 필드를 하위 호환 없이 변경하면 롤백 중 구버전 인스턴스가
  신버전 메시지를 못 읽는 상황이 생길 수 있다.
- **피처 플래그형 롤백**: `chainwatch.collector.enabled`, `chainwatch.notification.enabled`,
  `chainwatch.ai.enabled` 등은 즉시 끌 수 있는 스위치이므로, 특정 기능만 문제가 생겼을 때 전체
  롤백 대신 해당 기능만 비활성화하는 것도 1차 완화 수단으로 유효하다.

---

## 5. AWS 배포 옵션 및 권장안

### 5-1. 컴퓨트: ECS Fargate vs EKS

| 항목 | ECS Fargate | EKS |
|---|---|---|
| 운영 복잡도 | 낮음 (서버리스, 클러스터 관리 불필요) | 높음 (컨트롤 플레인/노드/애드온 관리) |
| 현재 규모 적합성 | 높음 — 서비스 3~4개(backend/frontend/ai-analysis/kafka-ui 등) 수준 | 과함 — 멀티팀/멀티클러스터 규모에 적합 |
| 비용 | 상대적으로 예측 가능 | 클러스터 관리 오버헤드 비용 추가 |
| CI/CD 연계 | ECR + ECS 배포 태스크로 단순 | Helm/ArgoCD 등 추가 도구 필요 |

**권장: ECS Fargate.** 현재 ChainWatch는 서비스 개수와 트래픽 규모가 크지 않고, 팀 규모상 Kubernetes
운영 오버헤드(노드 그룹, 오토스케일링 정책, 클러스터 애드온)를 감당할 이유가 아직 없다. 향후 서비스가
많아지고 멀티 리전/멀티 클러스터 요구가 생기면 그때 EKS로 전환을 재검토한다.

### 5-2. 나머지 구성 요소

- **RDS PostgreSQL**: Multi-AZ 옵션(운영 등급 시), 자동 백업 활성화, 파라미터 그룹에서 연결 수 제한
  확인. 현재 `HikariCP` 기본 풀 크기를 운영 인스턴스 수 × 풀 크기 합이 RDS `max_connections`를
  넘지 않도록 사전 계산 필요(요청 필요: 실제 인스턴스 수/풀 크기 결정 후 계산).
- **ElastiCache Redis**: dedupe/피드 캐시 용도이므로 클러스터 모드 비활성 단일 노드로 시작 가능.
  RUNBOOKS.md에 문서화한 대로 Redis 장애 시 알림이 fail-open(중복 발송)되므로, 가용성보다 "장애 시
  알림이 아예 끊기지 않는 것"이 우선순위인 현재 설계와 정합적이다 — Multi-AZ는 비용 대비 이득을
  검토해 결정.
- **MSK (Managed Kafka) 또는 대안**: 로컬 compose는 단일 브로커 KRaft 구성으로, 브로커 1대 장애가
  곧 전체 파이프라인 정지로 이어지는 알려진 한계가 있다(RUNBOOKS.md 2번 항목). 운영 환경에서는
  MSK(최소 3 브로커, 멀티 AZ) 또는 Confluent Cloud 등 관리형 서비스로 전환을 권장.
- **ALB + ACM**: TLS 종단 + 프론트엔드/백엔드 라우팅. 헬스체크 대상은 프론트엔드 `GET /`,
  백엔드는 별도 타겟 그룹으로 노출한다면 `GET /actuator/health`.
- **CloudWatch Logs**: 현재 로그는 stdout(logback-spring.xml 포맷)으로만 출력되므로 ECS
  awslogs 드라이버로 CloudWatch Logs에 연결하는 것만으로 즉시 연동 가능(코드 변경 불필요).
- **Prometheus/Grafana 운영 옵션**: 자체 호스팅 유지(EC2/ECS 컨테이너로 이전) 또는 Amazon Managed
  Service for Prometheus + Amazon Managed Grafana로 전환. 초기 단계에서는 자체 호스팅 유지가
  간단하고, 팀이 커지면 관리형 전환을 검토.

### 5-3. 종합 권장안 (요청 필요: 실행에는 AWS 계정/크리덴셜/도메인 필요)

```
ALB(ACM TLS) → ECS Fargate(frontend, backend, ai-analysis 태스크)
                → RDS PostgreSQL (Multi-AZ 선택)
                → ElastiCache Redis (단일 노드로 시작)
                → MSK (최소 3 브로커) 또는 관리형 Kafka 대안
              → CloudWatch Logs (awslogs 드라이버)
              → Prometheus/Grafana (자체 호스팅 유지 또는 AMP/AMG)
```

이 배포는 크리덴셜/도메인/AWS 계정 승인 없이는 실행하지 않았으며, 실행 전 반드시 사용자 승인이
필요하다.

---

## 6. CI/CD 현황 (읽기 전용 확인)

`.github/workflows/`에 이미 구성된 파이프라인 (기존 상태, 이번 작업에서 변경하지 않음):

| 워크플로 | 트리거 | 수행 내용 |
|---|---|---|
| `backend.yml` | `backend/**` 변경 | Gradle build + test |
| `frontend.yml` | `frontend/**` 변경 | npm ci → vitest → vite build |
| `ai-analysis-server.yml` | `ai/analysis-server/**` 변경 | pip install → pytest |

**현재 CI에 없는 것 (요청 필요/향후 과제)**: Docker 이미지 빌드 및 ECR 푸시 단계, 취약점 스캔
(Trivy/Grype 등), 배포 워크플로(ECS 태스크 정의 업데이트). 이는 별도 배포 파이프라인 구성 작업이며
이번 인프라 리뷰 범위에서는 구현하지 않았다.

---

## 7. 로컬 배포 검증 결과 (이번 점검에서 실행)

- `docker compose -f docker-compose.yml config --quiet` → **통과** (문법/구조 오류 없음)
- `docker compose up -d` 등 실제 컨테이너 기동은 이번 점검에서 실행하지 않음(장시간 실행 서비스
  기동 금지 지침에 따름) — 구성 파일 정적 검증만 수행했다.

---

## 8. 최종 체크리스트 요약

- [ ] `CHAINWATCH_JWT_ENABLED=true` + JWT 시크릿/관리자 비밀번호 교체
- [ ] Grafana 관리자 비밀번호 교체
- [ ] Postgres 운영 계정을 RDS 마스터 계정으로 교체(compose 기본값 사용 금지)
- [ ] Etherscan/RPC/AI 프로바이더 실 크리덴셜 발급 및 주입 (요청 필요)
- [ ] Slack/Discord 웹훅 URL 발급 (요청 필요)
- [ ] TLS 종단 지점 결정(ALB+ACM 권장) 및 도메인 준비 (요청 필요)
- [ ] DB 마이그레이션 전략 결정 — 최소한 `ddl-auto: validate` 전환 검토(백엔드 팀과 협의 필요)
- [ ] 배포 전 DB 백업/스냅샷 선행 절차 수립
- [ ] MSK 등 관리형 Kafka로 전환 여부 결정(단일 브로커 리스크 인지)
- [ ] CloudWatch Logs 연동(awslogs 드라이버 설정만으로 가능)
- [ ] CI에 이미지 빌드/취약점 스캔/배포 단계 추가(향후 과제)
