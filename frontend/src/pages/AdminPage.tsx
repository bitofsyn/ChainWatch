import { startTransition, useCallback, useEffect, useState } from "react";
import type { FormEvent } from "react";
import {
  ApiError,
  collectBlock,
  collectLatestBlock,
  fetchCollectorState,
  fetchDetectionRules,
  fetchEvents,
  fetchHealth,
  fetchPipelineStatus,
  fetchRecentEventFeed,
  fetchRecentTransactionFeed,
  login,
  requestAnalysis
} from "../api";
import type {
  CollectorResult,
  DetectionEventItem,
  DetectionRules,
  FeedEventItem,
  FeedTransactionItem,
  HealthResponse,
  PipelineStatus
} from "../types";
import type { AdminSection } from "../lib/router";
import { clearToken, isAdmin, setToken } from "../lib/auth";
import { formatDate, formatEventType, RISK_LEVEL_LABELS, shortenAddress } from "../lib/format";
import { RiskBadge } from "../components/RiskBadge";
import { StatusBadge } from "../components/StatusBadge";

const SECTION_ITEMS: { section: AdminSection; path: string; label: string }[] = [
  { section: "dashboard", path: "/admin", label: "운영 대시보드" },
  { section: "pipeline", path: "/admin/pipeline", label: "파이프라인 상태" },
  { section: "analysis", path: "/admin/analysis", label: "재분석" },
  { section: "policies", path: "/admin/policies", label: "탐지·알림 정책" }
];

interface AdminPageProps {
  section: AdminSection;
}

export function AdminPage({ section }: AdminPageProps) {
  const [authed, setAuthed] = useState(isAdmin());

  if (!authed) {
    return <AdminLogin onLogin={() => setAuthed(true)} />;
  }

  const handleLogout = () => {
    clearToken();
    setAuthed(false);
  };

  return (
    <>
      <section className="page-head">
        <div>
          <p className="eyebrow">관리자 콘솔</p>
          <h1>{SECTION_ITEMS.find((item) => item.section === section)?.label}</h1>
        </div>
        <div className="page-head-actions">
          <button type="button" className="ghost-button" onClick={handleLogout}>
            로그아웃
          </button>
        </div>
      </section>

      <nav className="sub-nav" aria-label="관리자 메뉴">
        {SECTION_ITEMS.map((item) => (
          <a
            key={item.section}
            href={`#${item.path}`}
            className={`nav-link ${section === item.section ? "active" : ""}`}
          >
            {item.label}
          </a>
        ))}
      </nav>

      {section === "dashboard" ? <AdminDashboard onUnauthorized={handleLogout} /> : null}
      {section === "pipeline" ? <AdminPipeline /> : null}
      {section === "analysis" ? <AdminReanalysis onUnauthorized={handleLogout} /> : null}
      {section === "policies" ? <AdminPolicies /> : null}
    </>
  );
}

function AdminLogin({ onLogin }: { onLogin: () => void }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const result = await login(username, password);
      setToken(result.accessToken);
      onLogin();
    } catch (cause) {
      const invalid = cause instanceof ApiError && cause.status === 401;
      setError(
        invalid
          ? "아이디 또는 비밀번호가 올바르지 않습니다."
          : "로그인 요청에 실패했습니다. 백엔드 상태를 확인해주세요."
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <section className="page-head">
        <div>
          <p className="eyebrow">관리자 콘솔</p>
          <h1>관리자 로그인</h1>
          <p className="page-lede">
            수집기 제어, 상태 변경, AI 재분석 등 운영 액션은 관리자 인증 후 사용할 수 있습니다.
          </p>
        </div>
      </section>

      <section className="login-card glass-card">
        <form className="login-form" onSubmit={handleSubmit}>
          <label>
            아이디
            <input
              type="text"
              value={username}
              autoComplete="username"
              onChange={(event) => setUsername(event.target.value)}
              required
            />
          </label>
          <label>
            비밀번호
            <input
              type="password"
              value={password}
              autoComplete="current-password"
              onChange={(event) => setPassword(event.target.value)}
              required
            />
          </label>
          {error ? <div className="banner error">{error}</div> : null}
          <button type="submit" className="primary-button" disabled={submitting}>
            {submitting ? "로그인 중..." : "로그인"}
          </button>
        </form>
      </section>
    </>
  );
}

function AdminDashboard({ onUnauthorized }: { onUnauthorized: () => void }) {
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
      if (cause instanceof ApiError && (cause.status === 401 || cause.status === 403)) {
        onUnauthorized();
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

function AdminReanalysis({ onUnauthorized }: { onUnauthorized: () => void }) {
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
      if (cause instanceof ApiError && (cause.status === 401 || cause.status === 403)) {
        onUnauthorized();
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

  return (
    <>
      <p className="hint-text">
        탐지 threshold와 알림 정책은 <span className="mono">application.yml</span>의{" "}
        <span className="mono">chainwatch.detection / chainwatch.notification</span> 설정으로
        관리하며, 변경은 배포 파이프라인을 통해 반영됩니다.
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
