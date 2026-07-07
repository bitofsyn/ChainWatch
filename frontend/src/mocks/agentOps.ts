import type {
  AgentHandoffEvent,
  AgentOpsAlert,
  AgentOpsSnapshot,
  AgentTeam,
  AgentTeamStatus
} from "../types";

function minutesAgo(minutes: number): string {
  return new Date(Date.now() - minutes * 60_000).toISOString();
}

function buildTeams(): AgentTeam[] {
  return [
    {
      id: "detection",
      name: "Detection Team",
      role: "온체인 트랜잭션 실시간 스크리닝",
      description:
        "수집 파이프라인이 넘겨준 블록/트랜잭션 스트림을 rule 기반 스크리너와 이상치 스코어러로 1차 판정하고, 임계치를 넘는 후보를 Analysis Team으로 핸드오프합니다.",
      status: "healthy",
      statusReason: null,
      queue: { queued: 14, inProgress: 6, retrying: 0, failedLastHour: 1, oldestWaitingSeconds: 22 },
      successRate1h: 99.2,
      avgProcessingMs: 850,
      throughputPerHour: 1240,
      lastHandoffTo: "analysis",
      inputTypes: ["block.stream", "tx.batch"],
      outputTypes: ["detection.candidate"],
      subAgents: [
        {
          id: "rule-screener",
          name: "rule-screener",
          role: "Rule Engine 스크리닝",
          state: "working",
          currentTask: "블록 20481240 트랜잭션 142건 판정"
        },
        {
          id: "anomaly-scorer",
          name: "anomaly-scorer",
          role: "이상치 점수 산정",
          state: "working",
          currentTask: "대규모 이체 후보 6건 스코어링"
        },
        {
          id: "watchlist-matcher",
          name: "watchlist-matcher",
          role: "watchlist 대조",
          state: "idle",
          currentTask: null
        }
      ],
      recentTasks: [
        {
          id: "det-1041",
          title: "블록 20481240 스크리닝",
          outcome: "in_progress",
          startedAt: minutesAgo(1),
          durationMs: null,
          detail: "트랜잭션 142건 중 3건 후보 판정 진행 중"
        },
        {
          id: "det-1040",
          title: "블록 20481239 스크리닝",
          outcome: "success",
          startedAt: minutesAgo(3),
          durationMs: 780,
          detail: "트랜잭션 131건 판정, LARGE_TRANSFER 후보 1건 → Analysis 핸드오프"
        },
        {
          id: "det-1039",
          title: "블록 20481238 스크리닝",
          outcome: "success",
          startedAt: minutesAgo(5),
          durationMs: 812,
          detail: "트랜잭션 118건 판정, 후보 없음"
        },
        {
          id: "det-1037",
          title: "watchlist 갱신 반영",
          outcome: "success",
          startedAt: minutesAgo(24),
          durationMs: 1930,
          detail: "감시 지갑 3건 추가, 매칭 캐시 재빌드"
        },
        {
          id: "det-1032",
          title: "블록 20481201 스크리닝",
          outcome: "failed",
          startedAt: minutesAgo(47),
          durationMs: 3020,
          detail: "노드 RPC timeout — 재수집 후 정상 처리됨"
        }
      ],
      recentFailures: [
        {
          id: "det-1032",
          title: "블록 20481201 스크리닝",
          outcome: "failed",
          startedAt: minutesAgo(47),
          durationMs: 3020,
          detail: "노드 RPC timeout — 재수집 후 정상 처리됨"
        }
      ],
      slaTargets: [
        { metric: "후보 판정 지연", target: "< 3초", current: "0.9초", met: true },
        { metric: "블록 처리 커버리지", target: "100%", current: "100%", met: true },
        { metric: "1시간 성공률", target: "≥ 99%", current: "99.2%", met: true }
      ],
      updatedAt: minutesAgo(0)
    },
    {
      id: "analysis",
      name: "Analysis Team",
      role: "탐지 후보 심층 분석·리스크 리포트 생성",
      description:
        "Detection Team이 넘긴 후보에 대해 자금 흐름 추적과 LLM 기반 해설을 수행하고, 위험 점수가 보강된 분석 리포트를 Triage Team으로 핸드오프합니다.",
      status: "degraded",
      statusReason: "LLM 분석 큐 적체 — 평균 처리 시간이 목표(90초)의 2배를 초과",
      queue: { queued: 38, inProgress: 4, retrying: 3, failedLastHour: 5, oldestWaitingSeconds: 540 },
      successRate1h: 91.4,
      avgProcessingMs: 186_000,
      throughputPerHour: 96,
      lastHandoffTo: "triage",
      inputTypes: ["detection.candidate"],
      outputTypes: ["analysis.report"],
      subAgents: [
        {
          id: "chain-tracer",
          name: "chain-tracer",
          role: "자금 흐름 추적",
          state: "working",
          currentTask: "이벤트 #482 연계 지갑 2-hop 추적"
        },
        {
          id: "llm-analyst-1",
          name: "llm-analyst-1",
          role: "LLM 리스크 해설",
          state: "working",
          currentTask: "이벤트 #479 리포트 생성"
        },
        {
          id: "llm-analyst-2",
          name: "llm-analyst-2",
          role: "LLM 리스크 해설",
          state: "error",
          currentTask: "이벤트 #476 분석 — provider timeout 재시도 3회째"
        },
        {
          id: "report-writer",
          name: "report-writer",
          role: "리포트 정리·저장",
          state: "idle",
          currentTask: null
        }
      ],
      recentTasks: [
        {
          id: "ana-0479",
          title: "이벤트 #479 심층 분석",
          outcome: "in_progress",
          startedAt: minutesAgo(4),
          durationMs: null,
          detail: "EXCHANGE_FLOW 후보 — 거래소 입금 경로 추적 중"
        },
        {
          id: "ana-0478",
          title: "이벤트 #478 심층 분석",
          outcome: "success",
          startedAt: minutesAgo(9),
          durationMs: 204_000,
          detail: "RAPID_TRANSFER 확정, 위험 점수 82 → Triage 핸드오프"
        },
        {
          id: "ana-0476",
          title: "이벤트 #476 심층 분석",
          outcome: "retrying",
          startedAt: minutesAgo(18),
          durationMs: null,
          detail: "LLM provider timeout — 3번째 재시도 대기"
        },
        {
          id: "ana-0475",
          title: "이벤트 #475 심층 분석",
          outcome: "failed",
          startedAt: minutesAgo(31),
          durationMs: 310_000,
          detail: "컨텍스트 초과로 리포트 생성 실패 — 입력 축약 필요"
        },
        {
          id: "ana-0474",
          title: "이벤트 #474 심층 분석",
          outcome: "success",
          startedAt: minutesAgo(40),
          durationMs: 178_000,
          detail: "WHALE_ACTIVITY 확정, 위험 점수 74"
        }
      ],
      recentFailures: [
        {
          id: "ana-0475",
          title: "이벤트 #475 심층 분석",
          outcome: "failed",
          startedAt: minutesAgo(31),
          durationMs: 310_000,
          detail: "컨텍스트 초과로 리포트 생성 실패 — 입력 축약 필요"
        },
        {
          id: "ana-0468",
          title: "이벤트 #468 심층 분석",
          outcome: "failed",
          startedAt: minutesAgo(72),
          durationMs: 300_500,
          detail: "LLM provider 5xx — 재시도 한도 초과, 수동 재분석 대기"
        }
      ],
      slaTargets: [
        { metric: "평균 분석 시간", target: "< 90초", current: "186초", met: false },
        { metric: "큐 최장 대기", target: "< 5분", current: "9분", met: false },
        { metric: "1시간 성공률", target: "≥ 97%", current: "91.4%", met: false }
      ],
      updatedAt: minutesAgo(0)
    },
    {
      id: "triage",
      name: "Triage Team",
      role: "분석 결과 등급 산정·대응 라우팅",
      description:
        "Analysis Team의 리포트를 받아 최종 위험 등급과 처리 우선순위를 확정하고, 경보가 필요한 건을 Notification Team으로, 운영 개입이 필요한 건을 Ops Team으로 라우팅합니다.",
      status: "healthy",
      statusReason: null,
      queue: { queued: 6, inProgress: 2, retrying: 0, failedLastHour: 0, oldestWaitingSeconds: 45 },
      successRate1h: 98.8,
      avgProcessingMs: 12_400,
      throughputPerHour: 88,
      lastHandoffTo: "notification",
      inputTypes: ["analysis.report"],
      outputTypes: ["alert.request", "ops.escalation"],
      subAgents: [
        {
          id: "risk-grader",
          name: "risk-grader",
          role: "최종 등급 산정",
          state: "working",
          currentTask: "이벤트 #478 등급 확정"
        },
        {
          id: "dedup-checker",
          name: "dedup-checker",
          role: "중복·연관 이벤트 병합",
          state: "working",
          currentTask: "동일 지갑 반복 탐지 3건 병합 검토"
        },
        {
          id: "router",
          name: "router",
          role: "대응 채널 라우팅",
          state: "idle",
          currentTask: null
        }
      ],
      recentTasks: [
        {
          id: "tri-0311",
          title: "이벤트 #478 등급 산정",
          outcome: "in_progress",
          startedAt: minutesAgo(2),
          durationMs: null,
          detail: "위험 점수 82 — CRITICAL 승격 여부 판단 중"
        },
        {
          id: "tri-0310",
          title: "이벤트 #477 등급 산정",
          outcome: "success",
          startedAt: minutesAgo(12),
          durationMs: 11_200,
          detail: "HIGH 확정 → Notification 핸드오프"
        },
        {
          id: "tri-0309",
          title: "이벤트 #474 등급 산정",
          outcome: "success",
          startedAt: minutesAgo(38),
          durationMs: 13_800,
          detail: "HIGH 확정, 동일 지갑 기존 이벤트와 병합"
        },
        {
          id: "tri-0308",
          title: "이벤트 #473 등급 산정",
          outcome: "success",
          startedAt: minutesAgo(55),
          durationMs: 9_400,
          detail: "MEDIUM 하향 — 거래소 내부 이체로 판정"
        }
      ],
      recentFailures: [],
      slaTargets: [
        { metric: "등급 확정 지연", target: "< 30초", current: "12초", met: true },
        { metric: "오분류율", target: "< 2%", current: "1.2%", met: true }
      ],
      updatedAt: minutesAgo(0)
    },
    {
      id: "notification",
      name: "Notification Team",
      role: "채널별 경보 발송(웹훅·메신저)",
      description:
        "Triage Team이 확정한 경보를 웹훅과 메신저 채널로 발송하고, 발송 실패 건을 재시도·에스컬레이션합니다.",
      status: "blocked",
      statusReason: "웹훅 엔드포인트 5xx 연속 — 발송 회로 차단(circuit open), Ops 에스컬레이션 완료",
      queue: { queued: 21, inProgress: 0, retrying: 8, failedLastHour: 17, oldestWaitingSeconds: 1260 },
      successRate1h: 38.5,
      avgProcessingMs: 2_300,
      throughputPerHour: 0,
      lastHandoffTo: "ops",
      inputTypes: ["alert.request"],
      outputTypes: ["alert.delivery", "ops.escalation"],
      subAgents: [
        {
          id: "webhook-sender",
          name: "webhook-sender",
          role: "웹훅 발송",
          state: "error",
          currentTask: "circuit open — 엔드포인트 복구 대기"
        },
        {
          id: "retry-manager",
          name: "retry-manager",
          role: "실패 건 재시도",
          state: "working",
          currentTask: "재시도 큐 8건 backoff 관리"
        },
        {
          id: "digest-writer",
          name: "digest-writer",
          role: "요약 다이제스트 작성",
          state: "idle",
          currentTask: null
        }
      ],
      recentTasks: [
        {
          id: "not-0552",
          title: "이벤트 #477 경보 발송",
          outcome: "retrying",
          startedAt: minutesAgo(8),
          durationMs: null,
          detail: "웹훅 503 응답 — backoff 재시도 대기"
        },
        {
          id: "not-0551",
          title: "이벤트 #474 경보 발송",
          outcome: "failed",
          startedAt: minutesAgo(21),
          durationMs: 4_100,
          detail: "웹훅 503 응답 3회 — 실패 처리, Ops 에스컬레이션"
        },
        {
          id: "not-0549",
          title: "이벤트 #473 경보 발송",
          outcome: "failed",
          startedAt: minutesAgo(26),
          durationMs: 3_900,
          detail: "웹훅 503 응답 3회 — 실패 처리"
        },
        {
          id: "not-0545",
          title: "이벤트 #471 경보 발송",
          outcome: "success",
          startedAt: minutesAgo(64),
          durationMs: 1_800,
          detail: "웹훅 발송 완료 (장애 발생 이전)"
        }
      ],
      recentFailures: [
        {
          id: "not-0551",
          title: "이벤트 #474 경보 발송",
          outcome: "failed",
          startedAt: minutesAgo(21),
          durationMs: 4_100,
          detail: "웹훅 503 응답 3회 — 실패 처리, Ops 에스컬레이션"
        },
        {
          id: "not-0549",
          title: "이벤트 #473 경보 발송",
          outcome: "failed",
          startedAt: minutesAgo(26),
          durationMs: 3_900,
          detail: "웹훅 503 응답 3회 — 실패 처리"
        }
      ],
      slaTargets: [
        { metric: "경보 발송 지연", target: "< 60초", current: "21분+ (차단)", met: false },
        { metric: "발송 성공률", target: "≥ 99%", current: "38.5%", met: false }
      ],
      updatedAt: minutesAgo(0)
    },
    {
      id: "ops",
      name: "Ops Team",
      role: "파이프라인 감시·장애 대응·에스컬레이션 처리",
      description:
        "전체 파이프라인 구성 요소를 감시하고, 다른 팀에서 올라온 에스컬레이션을 처리합니다. 반복 장애는 운영자에게 승격합니다.",
      status: "healthy",
      statusReason: null,
      queue: { queued: 3, inProgress: 1, retrying: 0, failedLastHour: 0, oldestWaitingSeconds: 300 },
      successRate1h: 100,
      avgProcessingMs: 45_000,
      throughputPerHour: 12,
      lastHandoffTo: null,
      inputTypes: ["ops.escalation", "pipeline.health"],
      outputTypes: ["ops.action", "operator.page"],
      subAgents: [
        {
          id: "health-watcher",
          name: "health-watcher",
          role: "구성 요소 헬스 체크",
          state: "working",
          currentTask: "웹훅 엔드포인트 복구 감시 (30초 주기)"
        },
        {
          id: "incident-handler",
          name: "incident-handler",
          role: "에스컬레이션 처리",
          state: "working",
          currentTask: "Notification 회로 차단 인시던트 대응"
        },
        {
          id: "escalator",
          name: "escalator",
          role: "운영자 승격",
          state: "idle",
          currentTask: null
        }
      ],
      recentTasks: [
        {
          id: "ops-0088",
          title: "Notification 회로 차단 대응",
          outcome: "in_progress",
          startedAt: minutesAgo(19),
          durationMs: null,
          detail: "웹훅 엔드포인트 복구 감시 중 — 미복구 시 운영자 승격 예정"
        },
        {
          id: "ops-0087",
          title: "Analysis 큐 적체 점검",
          outcome: "success",
          startedAt: minutesAgo(35),
          durationMs: 52_000,
          detail: "LLM provider 지연 확인 — 동시 실행 한도 상향 권고 기록"
        },
        {
          id: "ops-0086",
          title: "파이프라인 정기 헬스 체크",
          outcome: "success",
          startedAt: minutesAgo(60),
          durationMs: 8_200,
          detail: "Kafka/Redis/DB 정상, AI 서버 응답 지연 경고"
        }
      ],
      recentFailures: [],
      slaTargets: [
        { metric: "에스컬레이션 응답", target: "< 5분", current: "1.9분", met: true },
        { metric: "인시던트 승격 누락", target: "0건", current: "0건", met: true }
      ],
      updatedAt: minutesAgo(0)
    }
  ];
}

function buildHandoffs(): AgentHandoffEvent[] {
  return [
    {
      id: "ho-1024",
      fromTeamId: "detection",
      toTeamId: "analysis",
      subject: "LARGE_TRANSFER 후보 (블록 20481239)",
      reason: "이체 금액 임계치 초과 — 심층 분석 필요",
      result: "queued",
      occurredAt: minutesAgo(3)
    },
    {
      id: "ho-1023",
      fromTeamId: "analysis",
      toTeamId: "triage",
      subject: "이벤트 #478 분석 리포트",
      reason: "RAPID_TRANSFER 확정, 위험 점수 82",
      result: "accepted",
      occurredAt: minutesAgo(8)
    },
    {
      id: "ho-1022",
      fromTeamId: "triage",
      toTeamId: "notification",
      subject: "이벤트 #477 HIGH 경보",
      reason: "HIGH 등급 확정 — 웹훅 경보 발송 대상",
      result: "queued",
      occurredAt: minutesAgo(11)
    },
    {
      id: "ho-1021",
      fromTeamId: "notification",
      toTeamId: "ops",
      subject: "웹훅 발송 실패 인시던트",
      reason: "5xx 연속 실패로 circuit open — 운영 개입 필요",
      result: "accepted",
      occurredAt: minutesAgo(19)
    },
    {
      id: "ho-1020",
      fromTeamId: "triage",
      toTeamId: "notification",
      subject: "이벤트 #474 HIGH 경보",
      reason: "HIGH 등급 확정 — 웹훅 경보 발송 대상",
      result: "rejected",
      occurredAt: minutesAgo(22)
    },
    {
      id: "ho-1019",
      fromTeamId: "detection",
      toTeamId: "analysis",
      subject: "EXCHANGE_FLOW 후보 (블록 20481214)",
      reason: "거래소 핫월렛 대규모 입금 감지",
      result: "completed",
      occurredAt: minutesAgo(28)
    },
    {
      id: "ho-1018",
      fromTeamId: "analysis",
      toTeamId: "ops",
      subject: "LLM provider 지연 보고",
      reason: "평균 분석 시간 SLA 2배 초과 — 용량 점검 요청",
      result: "completed",
      occurredAt: minutesAgo(36)
    },
    {
      id: "ho-1017",
      fromTeamId: "analysis",
      toTeamId: "triage",
      subject: "이벤트 #474 분석 리포트",
      reason: "WHALE_ACTIVITY 확정, 위험 점수 74",
      result: "completed",
      occurredAt: minutesAgo(39)
    },
    {
      id: "ho-1016",
      fromTeamId: "triage",
      toTeamId: "ops",
      subject: "이벤트 #470 수동 검토 요청",
      reason: "등급 산정 신뢰도 낮음 — 운영자 판단 필요",
      result: "completed",
      occurredAt: minutesAgo(58)
    },
    {
      id: "ho-1015",
      fromTeamId: "detection",
      toTeamId: "analysis",
      subject: "WATCHLIST_MATCH 후보 (블록 20481168)",
      reason: "감시 지갑 발신 트랜잭션 감지",
      result: "completed",
      occurredAt: minutesAgo(71)
    }
  ];
}

function buildAlerts(teams: AgentTeam[]): AgentOpsAlert[] {
  const alerts: AgentOpsAlert[] = [];
  const notification = teams.find((team) => team.id === "notification");
  if (notification) {
    alerts.push({
      id: "alert-notification-circuit",
      severity: "critical",
      message: `Notification Team 발송 차단 — 경보 ${notification.queue.queued}건 대기, 최근 1시간 실패 ${notification.queue.failedLastHour}건`,
      teamId: "notification",
      raisedAt: minutesAgo(19)
    });
  }
  const analysis = teams.find((team) => team.id === "analysis");
  if (analysis) {
    alerts.push({
      id: "alert-analysis-backlog",
      severity: "warning",
      message: `Analysis Team 큐 적체 ${analysis.queue.queued}건 — 최장 대기 ${Math.round(analysis.queue.oldestWaitingSeconds / 60)}분`,
      teamId: "analysis",
      raisedAt: minutesAgo(12)
    });
  }
  return alerts;
}

const STATUS_WEIGHT: Record<AgentTeamStatus, number> = {
  blocked: 2,
  degraded: 1,
  healthy: 0
};

function pickBottleneck(teams: AgentTeam[]): string | null {
  const ranked = [...teams].sort(
    (a, b) =>
      STATUS_WEIGHT[b.status] - STATUS_WEIGHT[a.status] ||
      b.queue.oldestWaitingSeconds - a.queue.oldestWaitingSeconds
  );
  const top = ranked[0];
  if (!top || (top.status === "healthy" && top.queue.queued === 0)) {
    return null;
  }
  return top.id;
}

export function buildAgentOpsSnapshot(): AgentOpsSnapshot {
  const teams = buildTeams();
  const handoffs = buildHandoffs();
  return {
    overview: {
      generatedAt: new Date().toISOString(),
      activeTeams: teams.filter((team) => team.status !== "blocked").length,
      totalTeams: teams.length,
      throughputPerHour: teams.reduce((sum, team) => sum + team.throughputPerHour, 0),
      avgResponseMs: Math.round(
        teams.reduce((sum, team) => sum + team.avgProcessingMs, 0) / teams.length
      ),
      failed1h: teams.reduce((sum, team) => sum + team.queue.failedLastHour, 0),
      retried1h: teams.reduce((sum, team) => sum + team.queue.retrying, 0),
      bottleneckTeamId: pickBottleneck(teams),
      alerts: buildAlerts(teams)
    },
    teams,
    handoffs
  };
}
