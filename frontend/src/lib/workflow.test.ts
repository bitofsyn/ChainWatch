import { describe, expect, it } from "vitest";
import {
  buildStatusPatchBody,
  requiredReasonField,
  validateStatusChange,
  type StatusChangeDraft
} from "./workflow";

function draft(overrides: Partial<StatusChangeDraft> = {}): StatusChangeDraft {
  return {
    status: "ACKNOWLEDGED",
    assignee: "",
    resolutionReason: "",
    falsePositiveReason: "",
    notes: "",
    ...overrides
  };
}

describe("requiredReasonField", () => {
  it("requires resolutionReason only for RESOLVED", () => {
    expect(requiredReasonField("RESOLVED")).toBe("resolutionReason");
  });

  it("requires falsePositiveReason only for FALSE_POSITIVE", () => {
    expect(requiredReasonField("FALSE_POSITIVE")).toBe("falsePositiveReason");
  });

  it("requires no reason for other statuses", () => {
    expect(requiredReasonField("NEW")).toBeNull();
    expect(requiredReasonField("ACKNOWLEDGED")).toBeNull();
    expect(requiredReasonField("INVESTIGATING")).toBeNull();
  });
});

describe("validateStatusChange", () => {
  it("rejects RESOLVED without resolutionReason", () => {
    expect(validateStatusChange(draft({ status: "RESOLVED" }))).toContain("해결 사유");
    expect(validateStatusChange(draft({ status: "RESOLVED", resolutionReason: "   " }))).toContain(
      "해결 사유"
    );
  });

  it("rejects FALSE_POSITIVE without falsePositiveReason", () => {
    expect(validateStatusChange(draft({ status: "FALSE_POSITIVE" }))).toContain("오탐 사유");
  });

  it("accepts RESOLVED with reason and FALSE_POSITIVE with reason", () => {
    expect(
      validateStatusChange(draft({ status: "RESOLVED", resolutionReason: "정상 이체 확인" }))
    ).toBeNull();
    expect(
      validateStatusChange(
        draft({ status: "FALSE_POSITIVE", falsePositiveReason: "화이트리스트 지갑" })
      )
    ).toBeNull();
  });

  it("accepts non-terminal statuses without any reason", () => {
    expect(validateStatusChange(draft({ status: "INVESTIGATING" }))).toBeNull();
  });

  it("enforces max lengths", () => {
    expect(validateStatusChange(draft({ assignee: "a".repeat(101) }))).toContain("담당자");
    expect(
      validateStatusChange(
        draft({ status: "RESOLVED", resolutionReason: "r".repeat(501) })
      )
    ).toContain("해결 사유");
    expect(validateStatusChange(draft({ notes: "n".repeat(2001) }))).toContain("운영 메모");
  });
});

describe("buildStatusPatchBody", () => {
  it("builds RESOLVED body matching the PATCH contract", () => {
    const body = buildStatusPatchBody(
      draft({
        status: "RESOLVED",
        assignee: " alice ",
        resolutionReason: " 정상 이체 확인 ",
        notes: "메모"
      })
    );
    expect(body).toEqual({
      status: "RESOLVED",
      assignee: "alice",
      notes: "메모",
      resolutionReason: "정상 이체 확인"
    });
    expect(body).not.toHaveProperty("falsePositiveReason");
  });

  it("builds FALSE_POSITIVE body with falsePositiveReason only", () => {
    const body = buildStatusPatchBody(
      draft({ status: "FALSE_POSITIVE", falsePositiveReason: "내부 이동" })
    );
    expect(body.falsePositiveReason).toBe("내부 이동");
    expect(body).not.toHaveProperty("resolutionReason");
  });

  it("sends empty assignee to clear the assignment", () => {
    const body = buildStatusPatchBody(draft({ status: "ACKNOWLEDGED", assignee: "" }));
    expect(body.assignee).toBe("");
  });
});
