import { startTransition, useCallback, useEffect, useState } from "react";
import type { FormEvent } from "react";
import {
  ApiError,
  collectBlock,
  collectLatestBlock,
  fetchAuditLogs,
  fetchCollectorState,
  fetchDetectionRules,
  fetchEvents,
  fetchHealth,
  fetchPipelineStatus,
  fetchRecentEventFeed,
  fetchRecentTransactionFeed,
  requestAnalysis,
  updateDetectionThresholds
} from "../api";
import type { AuditLogFilters } from "../api";
import type {
  AuditLogItem,
  CollectorResult,
  DetectionEventItem,
  DetectionPolicyPatch,
  DetectionRules,
  DetectionThresholds,
  FeedEventItem,
  FeedTransactionItem,
  HealthResponse,
  PipelineStatus
} from "../types";
import type { AdminSection } from "../lib/router";
import { useAuth } from "../contexts/AuthContext";
import {
  formatDate,
  formatEventType,
  formatFullDate,
  RISK_LEVEL_LABELS,
  shortenAddress
} from "../lib/format";
import { formatNumber } from "../lib/opsOverview";
import { RiskBadge } from "../components/RiskBadge";
import { StatusBadge } from "../components/StatusBadge";
import { DataState } from "../components/DataState";
import { Pagination } from "../components/Pagination";
import { AdminUsersSection } from "./admin/AdminUsersSection";

const SECTION_ITEMS: { section: AdminSection; path: string; label: string; adminOnly?: boolean }[] = [
  { section: "dashboard", path: "/admin", label: "운영 대시보드" },
  { section: "pipeline", path: "/admin/pipeline", label: "파이프라인 상태" },
  { section: "analysis", path: "/admin/analysis", label: "재분석" },
  { section: "policies", path: "/admin/policies", label: "탐지·알림 정책" },
  { section: "audit", path: "/admin/audit", label: "감사 로그", adminOnly: true },
  { section: "users", path: "/admin/users", label: "사용자 관리", adminOnly: true }
];

interface AdminPageProps {
  section: AdminSection;
}

export function AdminPage({ section }: AdminPageProps) {
  const { isAdmin } = useAuth();

  // 익명 접근은 App의 라우트 가드가 #/login으로 보낸다.
  // ADMIN 전용 섹션에 ANALYST가 직접 진입하면 각 섹션이 forbidden 패널을 보여준다.
  const visibleSections = SECTION_ITEMS.filter((item) => !item.adminOnly || isAdmin);

  return (
    <>
      <section className="page-head">
        <div>
          <p className="eyebrow">관리자 콘솔</p>
          <h1>{SECTION_ITEMS.find((item) => item.section === section)?.label}</h1>
        </div>
      </section>

      <nav className="sub-nav" aria-label="관리자 메뉴">
        {visibleSections.map((item) => (
          <a
            key={item.section}
            href={`#${item.path}`}
            className={`nav-link ${section === item.section ? "active" : ""}`}
          >
            {item.label}
          </a>
        ))}
      </nav>

      {section === "dashboard" ? <AdminDashboard /> : null}
      {section === "pipeline" ? <AdminPipeline /> : null}
      {section === "analysis" ? <AdminReanalysis /> : null}
      {section === "policies" ? <AdminPolicies /> : null}
      {section === "audit" ? <AdminAuditLogs /> : null}
      {section === "users" ? <AdminUsersSection /> : null}
    </>
  );
}

function AdminDashboard() {
  const { isAdmin } = useAuth();
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [lastCollectedBlock, setLastCollectedBlock] = useState<number | null>(null);
  const [eventFeed, setEventFeed] = useState<FeedEventItem[]>([]);
  const [transactionFeed, setTransactionFeed] = useState<FeedTransactionItem[]>([]);
  const [error, setError] = useState<string | null>(null);

  const [blockInput, setBlockInput] = useState("");
  const [collecting, setCollecting] = useState(false);
  const [collectResult, setCollectResult] = useState<CollectorResult | null>(null);
  const [collectError, setCollectError] = useState<string | null>(null);

  const loadStatus = useCallback(async () => {
    try {
      const [healthData, stateData, eventFeedData, transactionFeedData] = await Promise.all([
        fetchHealth(),
        fetchCollectorState(),
        fetchRecentEventFeed(8),
        fetchRecentTransactionFeed(8)
      ]);
      startTransition(() => {
        setHealth(healthData);
        setLastCollectedBlock(stateData.lastCollectedBlock);
        setEventFeed(eventFeedData);
        setTransactionFeed(transactionFeedData);
        setError(null);
      });
    } catch {
      startTransition(() => {
        setError("시스템 상태 조회에 실패했습니다. 백엔드 상태를 확인해주세요.");
      });
    }
  }, []);

  useEffect(() => {
    loadStatus();
  }, [loadStatus]);

  const runCollection = async (action: () => Promise<CollectorResult>) => {
    setCollecting(true);
    setCollectError(null);
    setCollectResult(null);
    try {
      const result = await action();
      setCollectResult(result);
      await loadStatus();
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 403) {
        setCollectError("블록 수집 트리거는 ADMIN 권한 계정만 사용할 수 있습니다.");
        return;
      }
      const serverMessage = cause instanceof ApiError ? cause.serverMessage : null;
      setCollectError(serverMessage ?? "블록 수집 요청에 실패했습니다. 수집기/노드 상태를 확인해주세요.");
    } finally {
      setCollecting(false);
    }
  };

  const handleCollectBlock = (event: FormEvent) => {
    event.preventDefault();
    const blockNumber = Number(blockInput);
    if (!Number.isInteger(blockNumber) || blockNumber < 0) {
      setCollectError("올바른 블록 번호를 입력해주세요.");
      return;
    }
    runCollection(() => collectBlock(blockNumber));
  };

  return (
    <>
      {error ? <div className="banner error">{error}</div> : null}

      <section className="kpi-grid">
        <article className="metric-card">
          <span>백엔드 상태</span>
          <strong>{health?.status ?? "…"}</strong>
          <small>{health?.service ?? "확인 중"}</small>
        </article>
        <article className="metric-card">
          <span>마지막 수집 블록</span>
          <strong>{lastCollectedBlock != null && lastCollectedBlock >= 0 ? lastCollectedBlock : "없음"}</strong>
          <small>collector state</small>
        </article>
        <article className="metric-card">
          <span>최근 트랜잭션 피드</span>
          <strong>{transactionFeed.length}</strong>
          <small>Redis 캐시</small>
        </article>
        <article className="metric-card">
          <span>최근 이벤트 피드</span>
          <strong>{eventFeed.length}</strong>
          <small>Redis 캐시</small>
        </article>
      </section>

      <section className="grid-panel">
        {!isAdmin ? (
          <article className="glass-card">
            <div className="section-head compact">
              <div>
                <p className="section-kicker">수집 제어</p>
                <h2>블록 수집 트리거</h2>
              </div>
            </div>
            <div className="empty-state">블록 수집 트리거는 ADMIN 권한 계정만 사용할 수 있습니다.</div>
          </article>
        ) : (
        <article className="glass-card">
          <div className="section-head compact">
            <div>
              <p className="section-kicker">수집 제어</p>
              <h2>블록 수집 트리거</h2>
            </div>
          </div>

          <div className="admin-actions">
            <button
              type="button"
              className="primary-button"
              disabled={collecting}
              onClick={() => runCollection(collectLatestBlock)}
            >
              {collecting ? "수집 중..." : "최신 블록 수집"}
            </button>

            <form className="block-form" onSubmit={handleCollectBlock}>
              <input
                type="number"
                min="0"
                placeholder="블록 번호"
                value={blockInput}
                onChange={(event) => setBlockInput(event.target.value)}
                aria-label="블록 번호"
              />
              <button type="submit" className="ghost-button" disabled={collecting}>
                특정 블록 수집
              </button>
            </form>
          </div>

          {collectError ? <div className="banner error">{collectError}</div> : null}
          {collectResult ? (
            <div className="banner success">
              블록 {collectResult.blockNumber} 수집 완료 — 트랜잭션 {collectResult.transactionCount}건 중{" "}
              {collectResult.savedTransactionCount}건 저장 ({collectResult.network} /{" "}
              {collectResult.provider})
            </div>
          ) : null}
        </article>
        )}

        <article className="glass-card">
          <div className="section-head compact">
            <div>
              <p className="section-kicker">최근 탐지</p>
              <h2>이벤트 피드</h2>
            </div>
          </div>
          <div className="notes-feed">
            {eventFeed.length === 0 ? (
              <div className="empty-state">최근 탐지 이벤트가 없습니다.</div>
            ) : null}
            {eventFeed.map((item) => (
              <a className="note-item linked" key={item.eventId} href={`#/events/${item.eventId}`}>
                {formatEventType(item.eventType)} · 위험 점수 {item.riskScore}
                <small>{formatDate(item.detectedAt)}</small>
              </a>
            ))}
          </div>
        </article>

        <article className="glass-card wide-span">
          <div className="section-head compact">
            <div>
              <p className="section-kicker">최근 수집</p>
              <h2>트랜잭션 피드</h2>
            </div>
          </div>
          <div className="notes-feed">
            {transactionFeed.length === 0 ? (
              <div className="empty-state">최근 수집된 트랜잭션이 없습니다.</div>
            ) : null}
            {transactionFeed.map((item) => (
              <div className="note-item" key={item.txHash}>
                블록 {item.blockNumber} · <span className="mono">{item.txHash.slice(0, 18)}...</span> ·{" "}
                {item.amount} ETH
                <small>{formatDate(item.timestamp)}</small>
              </div>
            ))}
          </div>
        </article>
      </section>
    </>
  );
}

const COMPONENT_LABELS: Record<string, string> = {
  database: "PostgreSQL",
  redis: "Redis 피드 캐시",
  kafka: "Kafka 파이프라인",
  aiServer: "AI 분석 서버",
  collector: "블록 수집기",
  detection: "탐지 Rule Engine",
  notification: "알림 채널"
};

const COMPONENT_STATUS_LABELS: Record<string, string> = {
  UP: "정상",
  DOWN: "장애",
  DISABLED: "비활성"
};

function AdminPipeline() {
  const [status, setStatus] = useState<PipelineStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchPipelineStatus();
      startTransition(() => {
        setStatus(data);
        setError(null);
        setLoading(false);
      });
    } catch {
      startTransition(() => {
        setError("파이프라인 상태 조회에 실패했습니다.");
        setLoading(false);
      });
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <>
      <div className="section-toolbar">
        <p className="hint-text">
          수집 → Kafka → 탐지 → 저장/피드 → 알림/AI 해설로 이어지는 파이프라인의 구성 요소별
          상태입니다.
          {status ? ` 마지막 점검: ${formatDate(status.checkedAt)}` : ""}
        </p>
        <button type="button" className="ghost-button" onClick={load} disabled={loading}>
          {loading ? "점검 중..." : "다시 점검"}
        </button>
      </div>

      {error ? <div className="banner error">{error}</div> : null}
      {loading && !status ? <div className="empty-state">파이프라인 점검 중...</div> : null}

      <section className="pipeline-grid">
        {(status?.components ?? []).map((component) => (
          <article className={`glass-card component-card ${component.status.toLowerCase()}`} key={component.name}>
            <div className="component-head">
              <h2>{COMPONENT_LABELS[component.name] ?? component.name}</h2>
              <span className={`status-pill component-${component.status.toLowerCase()}`}>
                {COMPONENT_STATUS_LABELS[component.status] ?? component.status}
              </span>
            </div>
            <p>{component.detail}</p>
          </article>
        ))}
      </section>
    </>
  );
}

function AdminReanalysis() {
  const [events, setEvents] = useState<DetectionEventItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [runningId, setRunningId] = useState<number | null>(null);
  const [results, setResults] = useState<Record<number, string>>({});

  useEffect(() => {
    let active = true;

    async function load() {
      try {
        const [criticalPage, highPage] = await Promise.all([
          fetchEvents({ riskLevel: "CRITICAL" }, 10),
          fetchEvents({ riskLevel: "HIGH" }, 10)
        ]);
        if (!active) {
          return;
        }
        const merged = [...criticalPage.content, ...highPage.content]
          .sort((a, b) => b.riskScore - a.riskScore || b.detectedAt.localeCompare(a.detectedAt));
        startTransition(() => {
          setEvents(merged);
          setError(null);
          setLoading(false);
        });
      } catch {
        if (!active) {
          return;
        }
        startTransition(() => {
          setError("고위험 이벤트 조회에 실패했습니다.");
          setLoading(false);
        });
      }
    }

    load();
    return () => {
      active = false;
    };
  }, []);

  const handleReanalyze = async (eventId: number) => {
    setRunningId(eventId);
    setResults((current) => ({ ...current, [eventId]: "" }));
    try {
      await requestAnalysis(eventId);
      setResults((current) => ({ ...current, [eventId]: "분석 완료" }));
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 403) {
        setResults((current) => ({ ...current, [eventId]: "권한이 없습니다" }));
        return;
      }
      const serverMessage = cause instanceof ApiError ? cause.serverMessage : null;
      setResults((current) => ({
        ...current,
        [eventId]: serverMessage ?? "분석 실패"
      }));
    } finally {
      setRunningId(null);
    }
  };

  return (
    <>
      <p className="hint-text">
        CRITICAL/HIGH 등급 이벤트에 대해 AI 분석을 다시 실행합니다. 분석 결과는 각 이벤트
        상세 페이지의 AI 해설 섹션에 반영됩니다.
      </p>

      {error ? <div className="banner error">{error}</div> : null}
      {loading ? <div className="empty-state">불러오는 중...</div> : null}
      {!loading && events.length === 0 ? (
        <div className="empty-state">재분석 대상 고위험 이벤트가 없습니다.</div>
      ) : null}

      <section className="glass-card">
        <div className="table-wrap">
          {events.map((row) => (
            <div className="event-row detailed" key={row.id}>
              <div>
                <div className="badge-group">
                  <RiskBadge riskLevel={row.riskLevel} riskScore={row.riskScore} />
                  <StatusBadge status={row.status} />
                </div>
                <h3>
                  <a href={`#/events/${row.id}`}>
                    #{row.id} {formatEventType(row.eventType)}
                  </a>
                </h3>
              </div>
              <p>
                <span className="mono">{shortenAddress(row.walletAddress)}</span> · {row.summary}
              </p>
              <div className="wallet-col">
                <button
                  type="button"
                  className="ghost-button"
                  disabled={runningId !== null}
                  onClick={() => handleReanalyze(row.id)}
                >
                  {runningId === row.id ? "분석 중..." : "재분석"}
                </button>
                {results[row.id] ? <small>{results[row.id]}</small> : null}
              </div>
            </div>
          ))}
        </div>
      </section>
    </>
  );
}

const AUDIT_ACTION_LABELS: Record<string, string> = {
  EVENT_STATUS_CHANGE: "이벤트 상태 변경",
  COLLECTOR_COLLECT_LATEST: "최신 블록 수집",
  COLLECTOR_COLLECT_BLOCK: "블록 수집/재처리",
  DETECTION_THRESHOLD_UPDATE: "탐지 threshold 변경",
  LOGIN_SUCCESS: "로그인 성공",
  LOGIN_FAILURE: "로그인 실패"
};

const AUDIT_PAGE_SIZE = 20;

function AdminAuditLogs() {
  const [logs, setLogs] = useState<AuditLogItem[]>([]);
  const [filters, setFilters] = useState<AuditLogFilters>({});
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [forbidden, setForbidden] = useState(false);

  const load = useCallback(async (activeFilters: AuditLogFilters, activePage: number) => {
    setLoading(true);
    try {
      const data = await fetchAuditLogs(activeFilters, AUDIT_PAGE_SIZE, activePage);
      startTransition(() => {
        setLogs(data.content);
        setTotalPages(data.totalPages);
        setTotalElements(data.totalElements);
        setForbidden(false);
        setError(null);
        setLoading(false);
      });
    } catch (cause) {
      startTransition(() => {
        if (cause instanceof ApiError && cause.status === 403) {
          setForbidden(true);
          setError(null);
        } else if (cause instanceof ApiError && cause.status === 401) {
          setForbidden(true);
          setError(null);
        } else {
          setForbidden(false);
          setError("감사 로그 조회에 실패했습니다. 백엔드 상태를 확인해주세요.");
        }
        setLoading(false);
      });
    }
  }, []);

  useEffect(() => {
    load(filters, page);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  const handleSearch = (event: FormEvent) => {
    event.preventDefault();
    setPage(0);
    load(filters, 0);
  };

  return (
    <>
      <p className="hint-text">
        운영자 액션(상태 변경, 수집 트리거, 로그인)의 감사 기록입니다. ADMIN 권한 계정만 조회할 수
        있습니다.
      </p>

      <section className="panel-card">
        <form className="filter-bar audit-filter" onSubmit={handleSearch}>
          <input
            type="search"
            aria-label="수행자(actor) 검색"
            placeholder="수행자 (예: admin)"
            value={filters.actor ?? ""}
            onChange={(event) => setFilters({ ...filters, actor: event.target.value || undefined })}
          />
          <select
            aria-label="액션 유형"
            value={filters.action ?? ""}
            onChange={(event) => setFilters({ ...filters, action: event.target.value || undefined })}
          >
            <option value="">전체 액션</option>
            {Object.entries(AUDIT_ACTION_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
          <button type="submit" className="ghost-button">
            검색
          </button>
        </form>

        {!forbidden && !loading && !error ? (
          <p className="result-count">
            총 <strong>{formatNumber(totalElements)}</strong>건
          </p>
        ) : null}

        <DataState
          loading={loading && logs.length === 0}
          unauthorized={forbidden}
          unauthorizedMessage="감사 로그는 ADMIN 권한 계정만 조회할 수 있습니다. 현재 계정 권한을 확인해주세요."
          error={error}
          onRetry={() => load(filters, page)}
          empty={!loading && !error && !forbidden && logs.length === 0}
          emptyMessage="기록된 감사 로그가 없습니다."
        />

        {!forbidden && logs.length > 0 ? (
          <div className="table-scroll">
            <table className="data-table audit-table" aria-label="감사 로그">
              <thead>
                <tr>
                  <th scope="col">#</th>
                  <th scope="col">수행자</th>
                  <th scope="col">권한</th>
                  <th scope="col">액션</th>
                  <th scope="col">대상</th>
                  <th scope="col">상세</th>
                  <th scope="col">IP</th>
                  <th scope="col">시각</th>
                </tr>
              </thead>
              <tbody>
                {logs.map((row) => (
                  <tr key={row.id}>
                    <td className="num">{row.id}</td>
                    <td>{row.actor}</td>
                    <td className="cell-muted">{row.role ? row.role.replace(/^ROLE_/, "") : "-"}</td>
                    <td>
                      <span className="audit-action">{AUDIT_ACTION_LABELS[row.action] ?? row.action}</span>
                    </td>
                    <td className="mono">
                      {row.targetType}:{row.targetId}
                    </td>
                    <td className="cell-summary" title={row.detail ?? undefined}>
                      {row.detail ?? "-"}
                    </td>
                    <td className="mono">{row.clientIp ?? "-"}</td>
                    <td className="cell-time">{formatFullDate(row.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        {!forbidden ? <Pagination page={page} totalPages={totalPages} onChange={setPage} /> : null}
      </section>
    </>
  );
}

const THRESHOLD_FIELDS: {
  key: keyof DetectionThresholds;
  label: string;
  hint: string;
  step?: string;
}[] = [
  { key: "largeTransferThresholdEth", label: "대규모 이체 임계값", hint: "ETH", step: "0.1" },
  { key: "exchangeFlowThresholdEth", label: "거래소 플로우 임계값", hint: "ETH", step: "0.1" },
  { key: "rapidTransferThresholdCount", label: "반복 이체 기준 횟수", hint: "회" },
  { key: "rapidTransferWindowMinutes", label: "반복 이체 집계 창", hint: "분" },
  { key: "fanOutThresholdRecipients", label: "자금 분산 기준 수신자", hint: "주소" },
  { key: "fanOutWindowMinutes", label: "자금 분산 집계 창", hint: "분" },
  { key: "ruleCooldownMinutes", label: "룰 cooldown", hint: "분 (0=비활성)" }
];

function AdminPolicies() {
  const [rules, setRules] = useState<DetectionRules | null>(null);
  const [pipeline, setPipeline] = useState<PipelineStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    Promise.all([fetchDetectionRules(), fetchPipelineStatus()])
      .then(([rulesData, pipelineData]) => {
        if (active) {
          startTransition(() => {
            setRules(rulesData);
            setPipeline(pipelineData);
            setError(null);
            setLoading(false);
          });
        }
      })
      .catch(() => {
        if (active) {
          startTransition(() => {
            setError("정책 정보 조회에 실패했습니다.");
            setLoading(false);
          });
        }
      });

    return () => {
      active = false;
    };
  }, []);

  const notification = pipeline?.components.find((component) => component.name === "notification");
  const isAdmin = useAuth().isAdmin;

  return (
    <>
      <p className="hint-text">
        탐지 threshold와 주소 목록(watchlist·거래소)은 관리자 계정으로 아래에서 직접 수정할 수
        있으며, 변경 즉시 룰 평가에 반영되고 감사 로그에 기록됩니다. 알림 정책은{" "}
        <span className="mono">application.yml</span>의{" "}
        <span className="mono">chainwatch.notification</span> 설정으로 관리합니다.
      </p>

      {error ? <div className="banner error">{error}</div> : null}
      {loading ? <div className="empty-state">불러오는 중...</div> : null}

      {rules ? (
        <section className="glass-card">
          <div className="section-head compact">
            <div>
              <p className="section-kicker">탐지 정책</p>
              <h2>Rule Engine threshold</h2>
            </div>
            <span className="status-pill elevated">mode: {rules.mode}</span>
          </div>
          <div className="table-wrap">
            {rules.rules.map((rule) => (
              <div className="policy-row" key={rule.eventType}>
                <div>
                  <strong>{rule.name}</strong>
                  <small className="mono">{rule.eventType}</small>
                </div>
                <span>{rule.threshold}</span>
                <span>
                  {RISK_LEVEL_LABELS[rule.baseRiskLevel] ?? rule.baseRiskLevel} · {rule.baseRiskScore}점
                </span>
                <span className={`status-pill ${rule.active ? "elevated" : "lifecycle-resolved"}`}>
                  {rule.active ? "활성" : "조건 미설정"}
                </span>
              </div>
            ))}
          </div>
        </section>
      ) : null}

      {rules && isAdmin ? <ThresholdEditor rules={rules} onSaved={setRules} /> : null}

      {notification ? (
        <section className="glass-card policy-notification">
          <div className="section-head compact">
            <div>
              <p className="section-kicker">알림 정책</p>
              <h2>웹훅 알림 상태</h2>
            </div>
            <span className={`status-pill component-${notification.status.toLowerCase()}`}>
              {COMPONENT_STATUS_LABELS[notification.status] ?? notification.status}
            </span>
          </div>
          <p>{notification.detail}</p>
        </section>
      ) : null}
    </>
  );
}

const ETH_ADDRESS_PATTERN = /^0x[0-9a-fA-F]{40}$/;

const ADDRESS_FIELDS: { key: "watchlistAddresses" | "exchangeAddresses"; label: string; hint: string }[] = [
  {
    key: "watchlistAddresses",
    label: "watchlist 주소 (WHALE_ACTIVITY)",
    hint: "한 줄에 주소 1개. 비우면 룰 비활성"
  },
  {
    key: "exchangeAddresses",
    label: "거래소 주소 (EXCHANGE_FLOW)",
    hint: "한 줄에 주소 1개. 비우면 룰 비활성"
  }
];

function parseAddressLines(raw: string): string[] {
  return raw
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
}

function ThresholdEditor({
  rules,
  onSaved
}: {
  rules: DetectionRules;
  onSaved: (rules: DetectionRules) => void;
}) {
  const [draft, setDraft] = useState<Record<keyof DetectionThresholds, string>>(() =>
    toDraft(rules.thresholds)
  );
  const [addressDraft, setAddressDraft] = useState<Record<"watchlistAddresses" | "exchangeAddresses", string>>(
    () => toAddressDraft(rules)
  );
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setSuccess(null);

    const patch: DetectionPolicyPatch = {};
    for (const field of THRESHOLD_FIELDS) {
      const raw = draft[field.key].trim();
      const value = Number(raw);
      if (raw === "" || !Number.isFinite(value) || value < 0) {
        setFormError(`${field.label} 값이 올바르지 않습니다.`);
        return;
      }
      patch[field.key] = value;
    }
    for (const field of ADDRESS_FIELDS) {
      const addresses = parseAddressLines(addressDraft[field.key]);
      const invalid = addresses.find((address) => !ETH_ADDRESS_PATTERN.test(address));
      if (invalid) {
        setFormError(`${field.label} 형식이 올바르지 않습니다: ${invalid} (0x + 40자리 hex)`);
        return;
      }
      patch[field.key] = addresses;
    }

    setSaving(true);
    setFormError(null);
    try {
      const updated = await updateDetectionThresholds(patch);
      onSaved(updated);
      setDraft(toDraft(updated.thresholds));
      setAddressDraft(toAddressDraft(updated));
      setSuccess("탐지 정책을 변경했습니다. 변경 내역은 감사 로그에서 확인할 수 있습니다.");
    } catch (cause) {
      if (cause instanceof ApiError && (cause.status === 401 || cause.status === 403)) {
        setFormError("threshold 변경은 ADMIN 권한이 필요합니다. 로그인 상태를 확인해주세요.");
      } else if (cause instanceof ApiError && cause.serverMessage) {
        setFormError(cause.serverMessage);
      } else {
        setFormError("threshold 변경에 실패했습니다. 백엔드 상태를 확인해주세요.");
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="glass-card">
      <div className="section-head compact">
        <div>
          <p className="section-kicker">탐지 정책 수정</p>
          <h2>threshold 변경 (ADMIN)</h2>
        </div>
        {rules.thresholdsUpdatedAt ? (
          <small className="cell-muted">
            마지막 변경: {rules.thresholdsUpdatedBy ?? "-"} ·{" "}
            {formatFullDate(rules.thresholdsUpdatedAt)}
          </small>
        ) : (
          <small className="cell-muted">application.yml 기본값 적용 중</small>
        )}
      </div>

      <p className="hint-text">
        임계값을 낮추면 발화량이 급증해 분석 백로그·AI 쿼터에 부담이 될 수 있습니다. 저장 즉시
        룰 평가에 반영됩니다(재기동 불필요).
      </p>

      <form className="workflow-form" onSubmit={handleSubmit}>
        <div className="threshold-grid">
          {THRESHOLD_FIELDS.map((field) => (
            <label className="workflow-field" key={field.key}>
              {field.label} <em>({field.hint})</em>
              <input
                type="number"
                min="0"
                step={field.step ?? "1"}
                value={draft[field.key]}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, [field.key]: event.target.value }))
                }
              />
            </label>
          ))}
        </div>

        <div className="workflow-row">
          {ADDRESS_FIELDS.map((field) => (
            <label className="workflow-field" key={field.key}>
              {field.label} <em>({field.hint})</em>
              <textarea
                className="mono"
                rows={4}
                spellCheck={false}
                placeholder={"0x0000000000000000000000000000000000000000"}
                value={addressDraft[field.key]}
                onChange={(event) =>
                  setAddressDraft((current) => ({ ...current, [field.key]: event.target.value }))
                }
              />
            </label>
          ))}
        </div>

        {formError ? <div className="banner error">{formError}</div> : null}
        {success ? <div className="banner success">{success}</div> : null}

        <div className="workflow-submit">
          <button
            type="button"
            className="ghost-button"
            disabled={saving}
            onClick={() => {
              setDraft(toDraft(rules.thresholds));
              setAddressDraft(toAddressDraft(rules));
              setFormError(null);
              setSuccess(null);
            }}
          >
            되돌리기
          </button>
          <button type="submit" className="primary-button" disabled={saving}>
            {saving ? "저장 중..." : "threshold 저장"}
          </button>
        </div>
      </form>
    </section>
  );
}

function toAddressDraft(
  rules: DetectionRules
): Record<"watchlistAddresses" | "exchangeAddresses", string> {
  return {
    watchlistAddresses: (rules.watchlistAddresses ?? []).join("\n"),
    exchangeAddresses: (rules.exchangeAddresses ?? []).join("\n")
  };
}

function toDraft(thresholds: DetectionThresholds): Record<keyof DetectionThresholds, string> {
  return {
    largeTransferThresholdEth: String(thresholds.largeTransferThresholdEth),
    exchangeFlowThresholdEth: String(thresholds.exchangeFlowThresholdEth),
    rapidTransferThresholdCount: String(thresholds.rapidTransferThresholdCount),
    rapidTransferWindowMinutes: String(thresholds.rapidTransferWindowMinutes),
    fanOutThresholdRecipients: String(thresholds.fanOutThresholdRecipients),
    fanOutWindowMinutes: String(thresholds.fanOutWindowMinutes),
    ruleCooldownMinutes: String(thresholds.ruleCooldownMinutes)
  };
}
