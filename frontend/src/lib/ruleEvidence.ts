import { formatAmount, formatFullDate } from "./format";

/**
 * Wave2 룰 evidence JSON 렌더링 결정 로직 (순수 함수).
 * 룰별 스키마가 다르고 향후 키가 추가될 수 있으므로:
 * - 알려진 키는 한국어 라벨 + 값 포맷팅으로,
 * - 모르는 키는 원본 key/value 나열로 방어적으로 렌더링한다.
 */

export interface EvidenceEntry {
  key: string;
  label: string;
  value: string;
  /** 주소/해시/원본 키 등 고정폭 표기가 필요한 값 */
  mono: boolean;
  /** 알려진 키인지 (false면 raw fallback) */
  known: boolean;
}

const DIRECTION_LABELS: Record<string, string> = {
  INBOUND: "거래소 입금 (INBOUND)",
  OUTBOUND: "거래소 출금 (OUTBOUND)"
};

const MATCHED_DIRECTION_LABELS: Record<string, string> = {
  FROM: "watchlist 지갑이 송신 (FROM)",
  TO: "watchlist 지갑이 수신 (TO)"
};

const RULE_NAME_LABELS: Record<string, string> = {
  "large-transfer": "대규모 이체 룰",
  "exchange-flow": "거래소 입출금 룰",
  "rapid-transfer": "반복 이체 룰",
  "watchlist-activity": "watchlist 활동 룰"
};

interface KnownKeySpec {
  label: string;
  mono?: boolean;
  format?: (value: unknown) => string;
}

function asEth(value: unknown): string {
  return typeof value === "number" ? formatAmount(value) : String(value);
}

function asCount(value: unknown): string {
  return typeof value === "number" ? `${value}회` : String(value);
}

function asIsoDate(value: unknown): string {
  if (typeof value === "string" && !Number.isNaN(new Date(value).getTime())) {
    return formatFullDate(value);
  }
  return String(value);
}

/** 계약에 정의된 4개 룰(large-transfer / exchange-flow / rapid-transfer / watchlist-activity)의 키 */
const KNOWN_KEYS: Record<string, KnownKeySpec> = {
  rule: {
    label: "탐지 룰",
    format: (value) => {
      const raw = String(value);
      const known = RULE_NAME_LABELS[raw];
      return known ? `${known} (${raw})` : raw;
    }
  },
  ruleVersion: { label: "룰 버전", mono: true },
  thresholdEth: { label: "기준 금액", format: asEth },
  observedAmountEth: { label: "관측 금액", format: asEth },
  fromAddress: { label: "보낸 주소", mono: true },
  toAddress: { label: "받는 주소", mono: true },
  direction: {
    label: "자금 방향",
    format: (value) => DIRECTION_LABELS[String(value)] ?? String(value)
  },
  matchedExchangeAddress: { label: "매칭 거래소 주소", mono: true },
  counterpartyAddress: { label: "상대 주소", mono: true },
  windowMinutes: {
    label: "집계 창",
    format: (value) => (typeof value === "number" ? `${value}분` : String(value))
  },
  thresholdCount: { label: "기준 이체 횟수", format: asCount },
  observedTransferCount: { label: "관측 이체 횟수", format: asCount },
  windowStart: { label: "집계 시작 시각", format: asIsoDate },
  matchedAddress: { label: "watchlist 매칭 주소", mono: true },
  matchedDirection: {
    label: "매칭 방향",
    format: (value) => MATCHED_DIRECTION_LABELS[String(value)] ?? String(value)
  },
  watchlistReason: { label: "등재 사유", mono: true }
};

function stringifyUnknown(value: unknown): string {
  if (value == null) {
    return "-";
  }
  if (typeof value === "string") {
    return value;
  }
  if (typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

/**
 * evidence JSON을 표시용 항목 리스트로 변환.
 * 객체가 아니거나(null/배열/원시값) 비어 있으면 null → 기존 "근거 없음" 상태 렌더링.
 */
export function resolveRuleEvidence(evidence: unknown): EvidenceEntry[] | null {
  if (evidence == null || typeof evidence !== "object" || Array.isArray(evidence)) {
    return null;
  }
  const entries = Object.entries(evidence as Record<string, unknown>);
  if (entries.length === 0) {
    return null;
  }

  const known: EvidenceEntry[] = [];
  const unknown: EvidenceEntry[] = [];
  for (const [key, value] of entries) {
    const spec = KNOWN_KEYS[key];
    if (spec) {
      known.push({
        key,
        label: spec.label,
        value: spec.format ? spec.format(value) : stringifyUnknown(value),
        mono: spec.mono ?? false,
        known: true
      });
    } else {
      unknown.push({
        key,
        label: key,
        value: stringifyUnknown(value),
        mono: true,
        known: false
      });
    }
  }
  // 알려진 키 먼저, 모르는 키는 raw로 뒤에
  return [...known, ...unknown];
}

/* ── 트랜잭션 확정(confirmation) 판정 ─────────── */

export type ConfirmationKind = "confirmed" | "unconfirmed" | "unknown";

export interface ConfirmationView {
  kind: ConfirmationKind;
  label: string;
  /** 부가 설명 (있으면 툴팁/보조 텍스트로 노출) */
  detail: string | null;
}

/**
 * confirmed/confirmations → 확정 배지 뷰.
 * null(head 미관측)은 미확정(false)과 구분해 "판정 불가"로 처리한다 (Wave2 계약).
 */
export function resolveConfirmation(tx: {
  confirmations?: number | null;
  confirmed?: boolean | null;
}): ConfirmationView {
  if (tx.confirmed === true) {
    return {
      kind: "confirmed",
      label: "확정",
      detail: typeof tx.confirmations === "number" ? `${tx.confirmations} confirmations` : null
    };
  }
  if (tx.confirmed === false) {
    return {
      kind: "unconfirmed",
      label: "미확정",
      detail:
        typeof tx.confirmations === "number"
          ? `${tx.confirmations} confirmations — reorg 되감기 범위일 수 있음`
          : "reorg 되감기 범위일 수 있음"
    };
  }
  return {
    kind: "unknown",
    label: "판정 불가",
    detail: "수집기가 체인 head를 아직 관측하지 못했습니다"
  };
}
