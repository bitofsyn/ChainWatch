# Wave 1 Backend API Contracts (권한 분리 / 분석가 Workflow / 감사 로그)

작성: 2026-07-07, backend+security agent. 프론트엔드 에이전트가 참조하는 계약 문서.

## 1. 인증/권한 (Roles)

역할 2종: `ROLE_ADMIN`, `ROLE_ANALYST` (config 기반 사용자, JWT `roles` claim).

### 로그인 (변경 없음, 계정 추가)

`POST /api/auth/login` — body `{"username": "...", "password": "..."}` → `{"accessToken": "...", "tokenType": "Bearer", "expiresInSeconds": 3600}`

- 기본 dev 계정: `admin`/`chainwatch` → ROLE_ADMIN, `analyst`/`chainwatch` → ROLE_ANALYST
- 환경변수: `CHAINWATCH_ANALYST_USERNAME`, `CHAINWATCH_ANALYST_PASSWORD` (admin은 기존과 동일)

### 접근 규칙 (jwt-enabled=true일 때)

| 경로 | 권한 |
|---|---|
| `POST /api/collector/blocks/latest`, `POST /api/collector/blocks/{n}` | ADMIN 전용 |
| `GET /api/audit-logs` | ADMIN 전용 |
| 그 외 모든 `/api/**` (이벤트/트랜잭션/지갑/통계/피드/알림이력/agent-ops/AI 분석 트리거, collector state 조회 포함) | ANALYST 또는 ADMIN |
| `/api/auth/**`, swagger, actuator health/metrics | 공개 |

- 401 응답: `{"code": "UNAUTHORIZED", "message": "...", "timestamp": "..."}`
- 403 응답(신규): `{"code": "FORBIDDEN", "message": "You do not have permission to access this resource", "timestamp": "..."}`
- `jwt-enabled=false`(로컬 dev 기본)는 기존대로 전체 `/api/**` 허용. 단, `prod`/`production`/`prod-*`/`staging` 프로필로 기동하면 fail-fast로 거부.

## 2. 분석가 Workflow (DetectionEvent)

### EventStatus enum — 값 추가

`NEW | ACKNOWLEDGED | INVESTIGATING | RESOLVED | FALSE_POSITIVE` (FALSE_POSITIVE 신규)

### 상태 변경 API — 요청 필드 확장

`PATCH /api/events/{id}/status`

```json
{
  "status": "RESOLVED",            // 필수. EventStatus
  "assignee": "alice",             // 선택, max 100. null이면 기존 값 유지, ""이면 해제
  "resolutionReason": "...",       // status=RESOLVED일 때 필수, max 500
  "falsePositiveReason": "...",    // status=FALSE_POSITIVE일 때 필수, max 500
  "notes": "..."                   // 선택, max 2000. null이면 기존 값 유지
}
```

검증 실패 시 400 `{"code": "BAD_REQUEST", "message": "resolutionReason is required when status is RESOLVED"}` (또는 falsePositiveReason 동일 패턴).

### 이벤트 응답 — 필드 추가 (목록/상세 공통)

`GET /api/events` (content 요소), `GET /api/events/{id}`, PATCH 응답에 아래 필드 추가 (모두 nullable):

```json
{
  "...기존 필드...": "id, eventType, riskLevel, riskScore, summary, walletAddress, txHash, detectedAt, status",
  "assignee": "alice",
  "statusChangedAt": "2026-07-07T12:34:56Z",
  "resolutionReason": "Confirmed legitimate transfer",
  "falsePositiveReason": null,
  "notes": "cross-checked with hot wallet list"
}
```

상세(`GET /api/events/{id}`)는 기존처럼 `transactionId`, `aiReport` 포함.

### 이벤트 목록 필터 추가

`GET /api/events?status=NEW|ACKNOWLEDGED|INVESTIGATING|RESOLVED|FALSE_POSITIVE` — 신규 파라미터.
`status=NEW`는 레거시 null status 행도 포함한다. 기존 필터(eventType, riskLevel, wallet, from, to, page, size≤100)는 그대로.

## 3. 감사 로그 (신규)

`GET /api/audit-logs?actor=&action=&page=0&size=20` — ADMIN 전용, size 최대 100, createdAt desc 정렬. 표준 Spring Page 응답:

```json
{
  "content": [
    {
      "id": 1,
      "actor": "admin",             // 미인증 컨텍스트(dev 모드)에서는 "anonymous"
      "role": "ROLE_ADMIN",         // nullable
      "action": "EVENT_STATUS_CHANGE",
      "targetType": "DETECTION_EVENT",
      "targetId": "42",
      "detail": "NEW -> RESOLVED, assignee=alice",
      "clientIp": "127.0.0.1",      // nullable
      "createdAt": "2026-07-07T12:34:56Z"
    }
  ],
  "totalElements": 1, "totalPages": 1, "number": 0, "size": 20
}
```

기록되는 action 종류:

| action | targetType | targetId | 기록 시점 |
|---|---|---|---|
| `EVENT_STATUS_CHANGE` | `DETECTION_EVENT` | 이벤트 id | 상태 변경 성공 시 (검증 실패는 미기록) |
| `COLLECTOR_COLLECT_LATEST` | `COLLECTOR` | `latest` | 수동 최신 블록 수집 성공 시 |
| `COLLECTOR_COLLECT_BLOCK` | `COLLECTOR` | 블록 번호 | 수동 블록 수집/재처리 성공 시 |
| `LOGIN_SUCCESS` / `LOGIN_FAILURE` | `AUTH` | 시도한 username | 로그인 시 |

## 4. DB 변경 (additive만, JPA ddl-auto=update)

- `detection_events`: `assignee`, `status_changed_at`, `resolution_reason`, `false_positive_reason`, `notes` — 전부 nullable 신규 컬럼
- 신규 테이블 `audit_logs` (인덱스: created_at, actor, action)
- 기존 유니크 제약/인덱스 변경 없음
