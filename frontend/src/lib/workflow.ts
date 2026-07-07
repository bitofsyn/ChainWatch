import type { EventLifecycleStatus, EventStatusUpdateRequest } from "../types";

/** 상태 변경 폼 입력값 (컴포넌트 상태 그대로) */
export interface StatusChangeDraft {
  status: EventLifecycleStatus;
  assignee: string;
  resolutionReason: string;
  falsePositiveReason: string;
  notes: string;
}

export const ASSIGNEE_MAX = 100;
export const REASON_MAX = 500;
export const NOTES_MAX = 2000;

/** 상태별로 필수 사유 필드가 있는지 */
export function requiredReasonField(
  status: EventLifecycleStatus
): "resolutionReason" | "falsePositiveReason" | null {
  if (status === "RESOLVED") {
    return "resolutionReason";
  }
  if (status === "FALSE_POSITIVE") {
    return "falsePositiveReason";
  }
  return null;
}

/**
 * 상태 변경 입력 검증. 위반 시 사용자에게 보여줄 한국어 메시지, 통과 시 null.
 * 백엔드 검증(400)과 동일한 규칙을 클라이언트에서 선반영한다.
 */
export function validateStatusChange(draft: StatusChangeDraft): string | null {
  const reasonField = requiredReasonField(draft.status);
  if (reasonField === "resolutionReason" && !draft.resolutionReason.trim()) {
    return "해결(RESOLVED) 처리에는 해결 사유 입력이 필요합니다.";
  }
  if (reasonField === "falsePositiveReason" && !draft.falsePositiveReason.trim()) {
    return "오탐(FALSE_POSITIVE) 처리에는 오탐 사유 입력이 필요합니다.";
  }
  if (draft.assignee.trim().length > ASSIGNEE_MAX) {
    return `담당자는 최대 ${ASSIGNEE_MAX}자까지 입력할 수 있습니다.`;
  }
  if (draft.resolutionReason.trim().length > REASON_MAX) {
    return `해결 사유는 최대 ${REASON_MAX}자까지 입력할 수 있습니다.`;
  }
  if (draft.falsePositiveReason.trim().length > REASON_MAX) {
    return `오탐 사유는 최대 ${REASON_MAX}자까지 입력할 수 있습니다.`;
  }
  if (draft.notes.length > NOTES_MAX) {
    return `운영 메모는 최대 ${NOTES_MAX}자까지 입력할 수 있습니다.`;
  }
  return null;
}

/**
 * PATCH /api/events/{id}/status 요청 본문 생성 (Wave1 계약).
 * - assignee: 폼 값 그대로 전송. ""은 담당자 해제를 의미한다.
 * - 사유 필드: 해당 상태일 때만 포함 (그 외 상태에서는 null 유지).
 * - notes: 폼 값 그대로 전송 (폼이 기존 값으로 프리필되므로 안전).
 */
export function buildStatusPatchBody(draft: StatusChangeDraft): EventStatusUpdateRequest {
  const body: EventStatusUpdateRequest = {
    status: draft.status,
    assignee: draft.assignee.trim(),
    notes: draft.notes
  };
  if (draft.status === "RESOLVED") {
    body.resolutionReason = draft.resolutionReason.trim();
  }
  if (draft.status === "FALSE_POSITIVE") {
    body.falsePositiveReason = draft.falsePositiveReason.trim();
  }
  return body;
}
