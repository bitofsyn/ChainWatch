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
  fetchOperationalSettings,
  fetchPipelineStatus,
  fetchPolicySuggestions,
  fetchUsers,
  requestAnalysis,
  updateOperationalSettings,
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
  HealthResponse,
  PipelineStatus,
  OperationalSettings,
  PolicySuggestion,
  UserAccountItem
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
import { PolicySuggestionsPanel } from "../components/PolicySuggestionsPanel";

const SECTION_ITEMS: { section: AdminSection; path: string; label: string; adminOnly?: boolean }[] = [
  // 관리자가 바꿀 수 있는 것
  { section: "dashboard", path: "/admin", label: "개요", adminOnly: true },
  { section: "collection", path: "/admin/collection", label: "수집·알림", adminOnly: true },
  { section: "policies", path: "/admin/policies", label: "탐지 기준" },
  { section: "users", path: "/admin/users", label: "사용자 관리", adminOnly: true },
  { section: "audit", path: "/admin/audit", label: "감사 로그", adminOnly: true },
  // 상태 확인과 보조 작업
  { section: "pipeline", path: "/admin/pipeline", label: "파이프라인 상태" },
  { section: "analysis", path: "/admin/analysis", label: "AI 재분석" }
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
          <p className="eyebrow">{isAdmin ? "운영 설정" : "운영 콘솔"}</p>
          <h1>{SECTION_ITEMS.find((item) => item.section === section)?.label}</h1>
        </div>
      </section>

      <nav className="sub-nav" aria-label="운영 메뉴">
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

      {section === "dashboard" ? isAdmin ? <AdminDashboard /> : <AdminOnlyNotice /> : null}
      {section === "collection" ? isAdmin ? <AdminCollection /> : <AdminOnlyNotice /> : null}
      {section === "pipeline" ? <AdminPipeline /> : null}
      {section === "analysis" ? <AdminReanalysis /> : null}
      {section === "policies" ? <AdminPolicies /> : null}
      {section === "audit" ? <AdminAuditLogs /> : null}
      {section === "users" ? <AdminUsersSection /> : null}
    </>
  );
}

function AdminOnlyNotice() {
  return (
    <DataState
      unauthorized
      unauthorizedMessage="이 화면은 관리자만 사용할 수 있습니다. 분석가는 탐지 기준 조회, 파이프라인 상태, AI 재분석을 사용할 수 있습니다."
    />
  );
}

interface HubData {
  health: HealthResponse;
  lastCollectedBlock: number | null;
  settings: OperationalSettings;
  rules: DetectionRules;
  suggestions: PolicySuggestion[];
  users: UserAccountItem[];
  pipeline: PipelineStatus;
}

/**
 * 관리자 첫 화면. 관리자가 바꿀 수 있는 것(수집, 탐지 기준, 알림, 예외 주소, 사용자)의 현재 상태를
 * 한눈에 보여주고 각 설정 화면으로 보낸다.
 */
function AdminDashboard() {
  const [data, setData] = useState<HubData | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [health, state, settings, rules, suggestions, users, pipeline] = await Promise.all([
        fetchHealth(),
        fetchCollectorState(),
        fetchOperationalSettings(),
        fetchDetectionRules(),
        fetchPolicySuggestions(),
        fetchUsers(),
        fetchPipelineStatus()
      ]);
      startTransition(() => {
        setData({
          health,
          lastCollectedBlock: state.lastCollectedBlock,
          settings,
          rules,
          suggestions,
          users,
          pipeline
        });
        setError(null);
      });
    } catch {
      startTransition(() => setError("설정 현황을 불러오지 못했습니다. 백엔드 상태를 확인해주세요."));
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  if (!data) {
    return error ? <div className="banner error">{error}</div> : <div className="empty-state">불러오는 중...</div>;
  }

  const { settings, rules, suggestions, users, pipeline } = data;
  const activeRules = rules.rules.filter((rule) => rule.active).length;
  const pendingSuggestions = suggestions.filter((item) => item.status === "PENDING").length;
  const activeExclusions = suggestions.filter((item) => item.status === "ACCEPTED").length;
  const activeUsers = users.filter((user) => user.active);
  const countRole = (role: string) => activeUsers.filter((user) => user.role === role).length;
  const downComponents = pipeline.components.filter((component) => component.status === "DOWN");
  const channels = [settings.slackConfigured ? "Slack" : null, settings.discordConfigured ? "Discord" : null]
    .filter(Boolean)
    .join(" · ");
  const hasBlock = data.lastCollectedBlock != null && data.lastCollectedBlock >= 0;

  return (
    <>
      {error ? <div className="banner error">{error}</div> : null}
      <p className="hint-text">
        관리자가 바꿀 수 있는 설정의 현재 상태입니다. 변경은 재시작 없이 바로 적용되고 감사 로그에 기록됩니다.
      </p>

      <section className="settings-hub">
        <SettingCard
          title="블록 수집"
          state={settings.collectorEnabled ? "자동 수집 켜짐" : "자동 수집 꺼짐"}
          on={settings.collectorEnabled}
          detail={
            hasBlock
              ? `마지막 수집 블록 #${formatNumber(data.lastCollectedBlock as number)}`
              : "아직 수집한 블록이 없습니다. 수동 수집으로 데이터를 채울 수 있습니다."
          }
          href="#/admin/collection"
          action="수집 설정·수동 수집"
        />
        <SettingCard
          title="탐지 기준"
          state={`규칙 ${activeRules}/${rules.rules.length}개 활성`}
          on={activeRules > 0}
          detail={`대규모 이체 ${rules.thresholds.largeTransferThresholdEth} ETH 이상 · 반복 이체 ${rules.thresholds.rapidTransferWindowMinutes}분 내 ${rules.thresholds.rapidTransferThresholdCount}회 · 자금 분산 ${rules.thresholds.fanOutThresholdRecipients}개 주소`}
          href="#/admin/policies"
          action="임계값·주소 목록 수정"
        />
        <SettingCard
          title="알림"
          state={settings.notificationEnabled ? "웹훅 알림 켜짐" : "웹훅 알림 꺼짐"}
          on={settings.notificationEnabled}
          detail={`위험 점수 ${settings.minRiskScore}점 이상 알림 · ${channels || "연결된 채널 없음"}`}
          href="#/admin/collection"
          action="알림 채널 설정"
        />
        <SettingCard
          title="탐지 예외 주소"
          state={pendingSuggestions > 0 ? `검토 대기 ${pendingSuggestions}건` : "검토할 제안 없음"}
          on={pendingSuggestions > 0}
          attention={pendingSuggestions > 0}
          detail={`분석가가 제안한 오탐 주소입니다. 현재 예외 적용 중 ${activeExclusions}건.`}
          href="#/admin/policies"
          action="제안 검토"
        />
        <SettingCard
          title="사용자"
          state={`활성 계정 ${activeUsers.length}개`}
          on
          detail={`관리자 ${countRole("ADMIN")} · 분석가 ${countRole("ANALYST")} · 조회자 ${countRole("VIEWER")}`}
          href="#/admin/users"
          action="계정·비밀번호 관리"
        />
        <SettingCard
          title="시스템 상태"
          state={downComponents.length > 0 ? `장애 ${downComponents.length}건` : "정상 동작 중"}
          on={downComponents.length === 0}
          attention={downComponents.length > 0}
          detail={`백엔드 ${data.health.status} · 변경 이력은 감사 로그에서 확인합니다.`}
          href="#/admin/pipeline"
          action="파이프라인 상태"
          secondaryHref="#/admin/audit"
          secondaryAction="감사 로그"
        />
      </section>
    </>
  );
}

interface SettingCardProps {
  title: string;
  state: string;
  /** 켜짐/정상 상태면 강조색으로 표시 */
  on: boolean;
  /** 관리자의 조치가 필요한 상태 */
  attention?: boolean;
  detail: string;
  href: string;
  action: string;
  secondaryHref?: string;
  secondaryAction?: string;
}

function SettingCard({
  title,
  state,
  on,
  attention,
  detail,
  href,
  action,
  secondaryHref,
  secondaryAction
}: SettingCardProps) {
  return (
    <article className={`glass-card setting-card ${attention ? "needs-attention" : ""}`}>
      <p className="section-kicker">{title}</p>
      <strong className={`setting-state ${on ? "is-on" : "is-off"}`}>{state}</strong>
      <p className="setting-detail">{detail}</p>
      <div className="setting-actions">
        <a className="ghost-button" href={href}>
          {action}
        </a>
        {secondaryHref && secondaryAction ? (
          <a className="ghost-button" href={secondaryHref}>
            {secondaryAction}
          </a>
        ) : null}
      </div>
    </article>
  );
}

/** 수집·알림: 수동 블록 수집, 자동 수집 on/off, 웹훅 알림 설정. */
function AdminCollection() {
  const [lastCollectedBlock, setLastCollectedBlock] = useState<number | null>(null);
  const [settings, setSettings] = useState<OperationalSettings | null>(null);
  const [pipeline, setPipeline] = useState<PipelineStatus | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [blockInput, setBlockInput] = useState("");
  const [collecting, setCollecting] = useState(false);
  const [collectResult, setCollectResult] = useState<CollectorResult | null>(null);
  const [collectError, setCollectError] = useState<string | null>(null);

  const loadStatus = useCallback(async () => {
    try {
      const [stateData, settingsData, pipelineData] = await Promise.all([
        fetchCollectorState(),
        fetchOperationalSettings(),
        fetchPipelineStatus()
      ]);
      startTransition(() => {
        setLastCollectedBlock(stateData.lastCollectedBlock);
        setSettings(settingsData);
        setPipeline(pipelineData);
        setError(null);
      });
    } catch {
      startTransition(() => setError("수집·알림 설정을 불러오지 못했습니다. 백엔드 상태를 확인해주세요."));
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
      const serverMessage = cause instanceof ApiError ? cause.serverMessage : null;
      setCollectError(serverMessage ?? "블록 수집 요청에 실패했습니다. 수집기/노드 상태를 확인해주세요.");
    } finally {
      setCollecting(false);
    }
  };

  const handleCollectBlock = (event: FormEvent) => {
    event.preventDefault();
    const blockNumber = Number(blockInput);
    if (blockInput.trim() === "" || !Number.isInteger(blockNumber) || blockNumber < 0) {
      setCollectError("올바른 블록 번호를 입력해주세요.");
      return;
    }
    runCollection(() => collectBlock(blockNumber));
  };

  const notification = pipeline?.components.find((component) => component.name === "notification");
  const hasBlock = lastCollectedBlock != null && lastCollectedBlock >= 0;

  return (
    <>
      {error ? <div className="banner error">{error}</div> : null}

      <section className="glass-card">
        <div className="section-head compact">
          <div>
            <p className="section-kicker">수동 수집</p>
            <h2>블록 수집 트리거</h2>
          </div>
          <span className="status-pill elevated">
            {hasBlock ? `마지막 수집 #${formatNumber(lastCollectedBlock as number)}` : "수집 이력 없음"}
          </span>
        </div>
        <p className="hint-text">
          이더리움 메인넷 블록 하나를 가져와 저장하고 탐지 규칙을 실행합니다. 블록은 약 12초마다 생기므로, 최신 블록을
          여러 번 수집할 때는 간격을 두고 누르세요.
        </p>

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
            {collectResult.savedTransactionCount}건 저장 ({collectResult.network} / {collectResult.provider}).{" "}
            <a href="#/events">탐지된 이벤트 보기</a>
          </div>
        ) : null}
      </section>

      {settings ? (
        <OperationalSettingsEditor
          settings={settings}
          onSaved={async (updated) => {
            setSettings(updated);
            setPipeline(await fetchPipelineStatus());
          }}
        />
      ) : null}

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
  const isAdmin = useAuth().isAdmin;
  const [rules, setRules] = useState<DetectionRules | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    fetchDetectionRules()
      .then((rulesData) => {
        if (active) {
          startTransition(() => {
            setRules(rulesData);
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

  return (
    <>
      <p className="hint-text">
        {isAdmin
          ? "탐지 임계값과 주소 목록(watchlist·거래소)을 아래에서 수정하고, 분석가가 제안한 예외 주소를 검토합니다. 변경은 즉시 탐지에 반영되고 감사 로그에 기록됩니다."
          : "현재 적용 중인 탐지 기준과 예외 주소 제안 현황입니다. 기준 변경과 제안 승인은 관리자가 합니다."}
      </p>

      {error ? <div className="banner error">{error}</div> : null}
      {loading ? <div className="empty-state">불러오는 중...</div> : null}

      {rules ? (
        <section className="glass-card">
          <div className="section-head compact">
            <div>
              <p className="section-kicker">탐지 정책</p>
              <h2>적용 중인 탐지 규칙</h2>
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

      <PolicySuggestionsPanel />

      {rules && isAdmin ? <ThresholdEditor rules={rules} onSaved={setRules} /> : null}
    </>
  );
}

function OperationalSettingsEditor({
  settings,
  onSaved
}: {
  settings: OperationalSettings;
  onSaved: (settings: OperationalSettings) => Promise<void>;
}) {
  const [collectorEnabled, setCollectorEnabled] = useState(settings.collectorEnabled);
  const [notificationEnabled, setNotificationEnabled] = useState(settings.notificationEnabled);
  const [minRiskScore, setMinRiskScore] = useState(String(settings.minRiskScore));
  const [slackWebhookUrl, setSlackWebhookUrl] = useState("");
  const [discordWebhookUrl, setDiscordWebhookUrl] = useState("");
  const [clearSlack, setClearSlack] = useState(false);
  const [clearDiscord, setClearDiscord] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const score = Number(minRiskScore);
    if (!Number.isInteger(score) || score < 0 || score > 100) {
      setFormError("알림 위험 점수는 0~100 사이의 정수여야 합니다.");
      return;
    }
    setSaving(true);
    setFormError(null);
    setMessage(null);
    try {
      const updated = await updateOperationalSettings({
        collectorEnabled,
        notificationEnabled,
        minRiskScore: score,
        ...(slackWebhookUrl.trim() ? { slackWebhookUrl: slackWebhookUrl.trim() } : {}),
        ...(discordWebhookUrl.trim() ? { discordWebhookUrl: discordWebhookUrl.trim() } : {}),
        ...(clearSlack ? { slackWebhookUrl: "" } : {}),
        ...(clearDiscord ? { discordWebhookUrl: "" } : {})
      });
      setSlackWebhookUrl("");
      setDiscordWebhookUrl("");
      setClearSlack(false);
      setClearDiscord(false);
      await onSaved(updated);
      setMessage("운영 설정을 저장했습니다. 실행 중인 파이프라인에 바로 반영됩니다.");
    } catch (cause) {
      setFormError(cause instanceof ApiError && cause.serverMessage ? cause.serverMessage : "운영 설정 저장에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="glass-card">
      <div className="section-head compact">
        <div><p className="section-kicker">운영 제어</p><h2>수집·웹훅 설정</h2></div>
      </div>
      <form className="operational-settings-form" onSubmit={submit}>
        <label className="settings-toggle"><input type="checkbox" checked={collectorEnabled} onChange={(e) => setCollectorEnabled(e.target.checked)} /><span><strong>자동 블록 수집</strong><small>저장 즉시 polling 수집기의 실행 여부가 바뀝니다.</small></span></label>
        <label className="settings-toggle"><input type="checkbox" checked={notificationEnabled} onChange={(e) => setNotificationEnabled(e.target.checked)} /><span><strong>웹훅 알림</strong><small>Slack 또는 Discord 웹훅이 하나 이상 있어야 활성화할 수 있습니다.</small></span></label>
        <label><span>알림 위험 점수</span><input type="number" min="0" max="100" value={minRiskScore} onChange={(e) => setMinRiskScore(e.target.value)} /></label>
        <label><span>Slack 웹훅 {settings.slackConfigured ? "(설정됨)" : "(미설정)"}</span><input type="password" autoComplete="off" placeholder="https://hooks.slack.com/..." value={slackWebhookUrl} disabled={clearSlack} onChange={(e) => setSlackWebhookUrl(e.target.value)} /></label>
        {settings.slackConfigured ? <label className="inline-check"><input type="checkbox" checked={clearSlack} onChange={(e) => setClearSlack(e.target.checked)} /> 기존 Slack 웹훅 삭제</label> : null}
        <label><span>Discord 웹훅 {settings.discordConfigured ? "(설정됨)" : "(미설정)"}</span><input type="password" autoComplete="off" placeholder="https://discord.com/api/webhooks/..." value={discordWebhookUrl} disabled={clearDiscord} onChange={(e) => setDiscordWebhookUrl(e.target.value)} /></label>
        {settings.discordConfigured ? <label className="inline-check"><input type="checkbox" checked={clearDiscord} onChange={(e) => setClearDiscord(e.target.checked)} /> 기존 Discord 웹훅 삭제</label> : null}
        {formError ? <div className="banner error">{formError}</div> : null}
        {message ? <div className="banner success">{message}</div> : null}
        <div><button className="primary-button" type="submit" disabled={saving}>{saving ? "저장 중..." : "운영 설정 저장"}</button></div>
      </form>
    </section>
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
          <h2>임계값·주소 목록 수정</h2>
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
