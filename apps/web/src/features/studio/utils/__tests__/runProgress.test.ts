import { elapsedOf, formatSpan, phaseOf, remainingOf } from "@/features/studio/utils/runProgress";

const T0 = "2026-10-16T10:00:00Z";
const at = (s: number) => new Date(Date.parse(T0) + s * 1000);

describe("phaseOf", () => {
  it("shows what the executor reported", () => {
    expect(phaseOf({ status: "PROCESSING", phase: "encoding" })).toBe("encoding");
  });
  it("says queued until a job reports anything", () => {
    expect(phaseOf(undefined)).toBe("queued");
    expect(phaseOf({ status: "PENDING" })).toBe("queued");
  });
  it("a started job that has not said more is generating", () => {
    expect(phaseOf({ status: "PROCESSING" })).toBe("generating");
  });
});

describe("remainingOf", () => {
  const step = { startedAt: T0, estimateMs: 60_000, estimateSamples: 12 };
  it("counts down from the measured median", () => {
    expect(remainingOf(step, at(20))).toEqual({ tag: "left", ms: 40_000, samples: 12 });
  });
  it("says overdue past the median instead of inventing a new figure", () => {
    expect(remainingOf(step, at(75))).toEqual({ tag: "overdue", typicalMs: 60_000, samples: 12 });
  });
  it("gives no estimate with too few finished runs", () => {
    expect(remainingOf({ ...step, estimateSamples: 2 }, at(5))).toEqual({ tag: "none" });
  });
  it("gives no estimate when the server sent none or the step has not started", () => {
    expect(remainingOf({ startedAt: T0 }, at(5))).toEqual({ tag: "none" });
    expect(remainingOf({ ...step, startedAt: undefined }, at(5))).toEqual({ tag: "none" });
  });
});

describe("formatSpan / elapsedOf", () => {
  it("reads like a person", () => {
    expect(formatSpan(42_000)).toBe("42 s");
    expect(formatSpan(185_000)).toBe("3 min 05 s");
    expect(formatSpan(120_000)).toBe("2 min");
  });
  it("elapsed is null before dispatch and never negative", () => {
    expect(elapsedOf({ startedAt: undefined }, at(5))).toBeNull();
    expect(elapsedOf({ startedAt: T0 }, at(-5))).toBe(0);
    expect(elapsedOf({ startedAt: T0 }, at(9))).toBe(9000);
  });
});
