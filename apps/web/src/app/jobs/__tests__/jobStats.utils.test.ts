import { computeJobStats, formatMs } from "@/app/jobs/jobStats.utils";
import type { Job } from "@/core/types/jobs.types";

jest.mock("@/core/constants/http.constants", () => ({
  JOB_STATUS: {
    COMPLETED: "COMPLETED",
    FAILED: "FAILED",
    PENDING: "PENDING",
    PROCESSING: "PROCESSING",
  },
}));

function createJob(overrides: Partial<Job> = {}): Job {
  return {
    id: "job-1",
    featureKey: "chat.text",
    status: "COMPLETED",
    createdAt: "2026-01-01T00:00:00.000Z",
    updatedAt: "2026-01-01T00:00:05.000Z",
    payload: { model: "llama3" },
    result: null,
    ...overrides,
  } as Job;
}

describe("formatMs", () => {
  it("returns dash for zero", () => {
    expect(formatMs(0)).toBe("—");
  });

  it("formats milliseconds", () => {
    expect(formatMs(500)).toBe("500ms");
  });

  it("formats seconds", () => {
    expect(formatMs(3500)).toBe("3.5s");
  });

  it("formats minutes", () => {
    expect(formatMs(125_000)).toBe("2m 5s");
  });

  it("formats hours", () => {
    expect(formatMs(3_661_000)).toBe("1h 1m");
  });

  it("formats sub-second correctly", () => {
    expect(formatMs(999)).toBe("999ms");
  });

  it("formats exactly 60 seconds as 1m 0s", () => {
    expect(formatMs(60_000)).toBe("1m 0s");
  });
});

describe("computeJobStats", () => {
  it("returns zero stats for empty array", () => {
    const stats = computeJobStats([]);
    expect(stats.total).toBe(0);
    expect(stats.completed).toBe(0);
    expect(stats.failed).toBe(0);
    expect(stats.avgMs).toBe(0);
    expect(stats.avgDuration).toBe("—");
    expect(stats.uniqueModels).toBe(0);
    expect(stats.uniqueFeatures).toBe(0);
    expect(stats.topModel).toBeNull();
    expect(stats.topFeature).toBeNull();
  });

  it("counts completed and failed jobs", () => {
    const jobs = [
      createJob({ status: "COMPLETED" }),
      createJob({ id: "j2", status: "COMPLETED" }),
      createJob({ id: "j3", status: "FAILED" }),
      createJob({ id: "j4", status: "PENDING" }),
    ];
    const stats = computeJobStats(jobs);
    expect(stats.total).toBe(4);
    expect(stats.completed).toBe(2);
    expect(stats.failed).toBe(1);
  });

  it("computes average duration from timestamps", () => {
    const jobs = [
      createJob({ createdAt: "2026-01-01T00:00:00.000Z", updatedAt: "2026-01-01T00:00:02.000Z" }),
      createJob({ id: "j2", createdAt: "2026-01-01T00:00:00.000Z", updatedAt: "2026-01-01T00:00:04.000Z" }),
    ];
    const stats = computeJobStats(jobs);
    expect(stats.avgMs).toBe(3000); // (2000 + 4000) / 2
  });

  it("identifies top model", () => {
    const jobs = [
      createJob({ payload: { model: "llama3" } }),
      createJob({ id: "j2", payload: { model: "llama3" } }),
      createJob({ id: "j3", payload: { model: "gpt4" } }),
    ];
    const stats = computeJobStats(jobs);
    expect(stats.topModel).toBe("⚡ llama3");
    expect(stats.uniqueModels).toBe(2);
  });

  it("identifies top feature", () => {
    const jobs = [
      createJob({ featureKey: "chat.text" }),
      createJob({ id: "j2", featureKey: "chat.text" }),
      createJob({ id: "j3", featureKey: "media.image" }),
    ];
    const stats = computeJobStats(jobs);
    expect(stats.topFeature).toBe("⚡ text");
    expect(stats.uniqueFeatures).toBe(2);
  });

  it("computes max duration", () => {
    const jobs = [
      createJob({ createdAt: "2026-01-01T00:00:00.000Z", updatedAt: "2026-01-01T00:00:01.000Z" }),
      createJob({ id: "j2", createdAt: "2026-01-01T00:00:00.000Z", updatedAt: "2026-01-01T00:00:10.000Z" }),
    ];
    const stats = computeJobStats(jobs);
    expect(stats.maxDuration).toBe("10.0s");
  });

  it("handles jobs without model", () => {
    const jobs = [createJob({ payload: {} })];
    const stats = computeJobStats(jobs);
    expect(stats.topModel).toBeNull();
    expect(stats.uniqueModels).toBe(0);
  });

  it("handles jobs without timestamps gracefully", () => {
    const jobs = [createJob({ createdAt: undefined as never, updatedAt: undefined as never })];
    const stats = computeJobStats(jobs);
    expect(stats.avgMs).toBe(0);
  });

  it("uses result.durationMs as fallback", () => {
    const jobs = [
      createJob({
        status: "PENDING",
        createdAt: undefined as never,
        updatedAt: undefined as never,
        result: { durationMs: 1500 } as never,
      }),
    ];
    const stats = computeJobStats(jobs);
    expect(stats.avgMs).toBe(1500);
  });
});
