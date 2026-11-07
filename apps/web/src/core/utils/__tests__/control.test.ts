import { controlLabel, parseControl } from "@/core/utils/control";

describe("parseControl", () => {
  it("reads the verdict the engine puts on a chunk", () => {
    expect(parseControl({ "control.verdict": "PENDING" })).toEqual({ state: "pending" });
    expect(parseControl({ "control.verdict": "VALIDATED", provider: "ollama" })).toEqual({ state: "validated" });
    expect(parseControl({ "control.verdict": "NOT_CHECKED" })).toEqual({ state: "not_checked" });
  });
  it("keeps the badge of a degraded verification", () => {
    expect(parseControl({ "control.verdict": "DEGRADED", "control.badge": "verification_unavailable" })).toEqual({
      state: "degraded",
      badge: "verification_unavailable",
    });
  });
  it("says nothing when the chunk does not", () => {
    expect(parseControl(undefined)).toBeNull();
    expect(parseControl({})).toBeNull();
    expect(parseControl({ "control.verdict": "SOMETHING_ELSE" })).toBeNull();
    expect(parseControl({ "control.verdict": 3 })).toBeNull();
  });
});

describe("controlLabel", () => {
  it("shows « en vérification » only while the answer streams", () => {
    expect(controlLabel({ state: "pending" }, true)).toBe("pending");
    expect(controlLabel({ state: "pending" }, false)).toBeNull();
  });
  it("shows the verdict once it is final", () => {
    expect(controlLabel({ state: "validated" }, false)).toBe("validated");
    expect(controlLabel({ state: "degraded", badge: "verification_unavailable" }, false)).toBe("unavailable");
  });
  it("shows nothing when no control ran", () => {
    expect(controlLabel({ state: "not_checked" }, false)).toBeNull();
    expect(controlLabel(undefined, true)).toBeNull();
  });
});
