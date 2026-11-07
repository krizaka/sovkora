import type { Run } from "@krizaka/orazaka-shared";
import { csvCell, usageCsv, usageJson, usageRows, usageSummary } from "@/core/jobs/usageStats";
import type { Job } from "@/core/types/jobs.types";

const NOW = new Date(2026, 9, 16, 12, 0, 0);
const iso = (day: number, h = 10, m = 0) => new Date(2026, 9, day, h, m, 0).toISOString();
const job = (id: string, day: number, status: Job["status"], minutes = 1): Job => ({
  id,
  userId: "u",
  featureKey: "image.flux",
  status,
  payload: {},
  result: {},
  createdAt: iso(day, 10, 0),
  updatedAt: iso(day, 10, minutes),
});
const run = (id: string, day: number, status = "COMPLETED"): Run =>
  ({ id, studioKey: "=cmd|' /C calc'!A0", status, startedAt: iso(day) }) as unknown as Run;

const jobs = [
  job("a", 15, "COMPLETED", 2),
  job("b", 14, "COMPLETED", 4),
  job("c", 14, "FAILED"),
  job("d", 6, "COMPLETED", 1),
  job("old", 1, "COMPLETED"),
];

describe("usageSummary", () => {
  it("counts the period and the one before it, and the median time to a result", () => {
    const { current, previous, perDay } = usageSummary(jobs, [run("r1", 15)], 7, NOW);
    expect(current).toMatchObject({ jobs: 3, completed: 2, failed: 1, runs: 1, medianSeconds: 180 });
    expect(current.successRate).toBeCloseTo(2 / 3);
    expect(previous).toMatchObject({ jobs: 1, completed: 1, failed: 0, successRate: 1, medianSeconds: 60 });
    expect(perDay).toHaveLength(7);
    expect(perDay.at(-1)?.day).toBe("2026-10-16");
  });

  it("says nothing it cannot back: no finished job, no rate and no time", () => {
    const { current } = usageSummary([job("x", 16, "RUNNING" as Job["status"])], [], 7, NOW);
    expect(current).toMatchObject({ jobs: 1, successRate: null, medianSeconds: null });
  });
});

describe("export", () => {
  it("neutralises a formula in a CSV cell", () => {
    expect(csvCell("=SUM(A1)")).toBe("'=SUM(A1)");
    expect(csvCell("+1")).toBe("'+1");
    expect(csvCell("-2")).toBe("'-2");
    expect(csvCell("@cmd")).toBe("'@cmd");
    expect(csvCell("a,b")).toBe('"a,b"');
    expect(csvCell('say "hi"')).toBe('"say ""hi"""');
    expect(csvCell(null)).toBe("");
    expect(csvCell(12.5)).toBe("12.5");
  });

  it("writes one row per job and run of the period, nothing outside it", () => {
    const csv = usageCsv(jobs, [run("r1", 15)], 7, NOW);
    const lines = csv.replace("﻿", "").trim().split("\n");
    expect(lines[0]).toBe("type,id,started_at,name,kind,status,seconds");
    expect(lines).toHaveLength(1 + 3 + 1);
    expect(csv).not.toContain("old");
    expect(lines.find((l) => l.startsWith("studio_run"))).toContain("'=cmd|' /C calc'!A0");
  });

  it("writes the same period as JSON", () => {
    const data = JSON.parse(usageJson(jobs, [run("r1", 15)], 7, NOW));
    expect(data.period).toEqual({ days: 7, to: "2026-10-16" });
    expect(data.totals.jobs).toBe(3);
    expect(data.jobs).toHaveLength(3);
    expect(usageRows(jobs, [], 7, NOW).jobs[0].id).toBe("a");
  });
});
