import type { Run } from "@krizaka/orazaka-shared";
import {
  activityByDay,
  formatEta,
  jobDuration,
  jobsByKind,
  remainingFor,
  runsByStudio,
  timeToResultByKind,
} from "@/core/jobs/jobInsights";
import type { Job } from "@/core/types/jobs.types";

const NOW = new Date("2026-10-11T12:00:00Z");

function job(over: Partial<Job>): Job {
  return {
    id: over.id ?? Math.random().toString(36),
    userId: "eric",
    featureKey: "orazaka.core.media.image",
    status: "COMPLETED",
    payload: {},
    result: {},
    createdAt: "2026-10-11T10:00:00Z",
    updatedAt: "2026-10-11T10:01:00Z",
    ...over,
  };
}

describe("activityByDay", () => {
  it("lays out every day of the window, oldest first, with the jobs by outcome", () => {
    const rows = activityByDay(
      [
        job({ createdAt: "2026-10-11T08:00:00Z" }),
        job({ createdAt: "2026-10-11T09:00:00Z", status: "FAILED" }),
        job({ createdAt: "2026-10-10T09:00:00Z", status: "PROCESSING" }),
        job({ createdAt: "2026-09-01T09:00:00Z" }),
      ],
      3,
      NOW,
    );
    expect(rows.map((r) => r.day)).toHaveLength(3);
    expect(rows[2]).toMatchObject({ completed: 1, failed: 1, running: 0 });
    expect(rows[1]).toMatchObject({ completed: 0, failed: 0, running: 1 });
    expect(rows[0]).toMatchObject({ completed: 0, failed: 0, running: 0 });
  });
});

describe("jobsByKind", () => {
  it("counts each kind of work, largest first", () => {
    expect(
      jobsByKind([
        job({}),
        job({}),
        job({ featureKey: "orazaka.core.media.speech" }),
        job({ featureKey: "orazaka.core.chat.completion" }),
      ]),
    ).toEqual([
      { kind: "image", count: 2 },
      { kind: "audio", count: 1 },
      { kind: "text", count: 1 },
    ]);
  });
});

describe("durations", () => {
  it("measures only a completed job", () => {
    expect(jobDuration(job({}))).toBe(60_000);
    expect(jobDuration(job({ status: "FAILED" }))).toBeNull();
  });

  it("gives the median time to a result per kind, in seconds", () => {
    const timing = timeToResultByKind([
      job({ updatedAt: "2026-10-11T10:01:00Z" }),
      job({ updatedAt: "2026-10-11T10:02:00Z" }),
      job({ featureKey: "orazaka.core.media.speech", updatedAt: "2026-10-11T10:00:02Z" }),
    ]);
    expect(timing).toEqual([
      { kind: "image", seconds: 90, samples: 2 },
      { kind: "audio", seconds: 2, samples: 1 },
    ]);
  });
});

describe("remainingFor", () => {
  const history = [job({}), job({ updatedAt: "2026-10-11T10:03:00Z" }), job({ updatedAt: "2026-10-11T10:02:00Z" })];

  it("counts down from the median the same work took here", () => {
    const running = job({ status: "PROCESSING", createdAt: "2026-10-11T11:59:30Z" });
    expect(remainingFor(running, history, NOW)).toBe(90_000);
  });

  it("follows the worker's percentage when it reports one", () => {
    const running = job({ status: "PROCESSING", createdAt: "2026-10-11T11:59:00Z", progress: 50 });
    expect(remainingFor(running, history, NOW)).toBe(60_000);
  });

  it("says nothing when no comparable work has finished", () => {
    expect(
      remainingFor(job({ status: "PROCESSING", featureKey: "orazaka.core.media.video" }), history, NOW),
    ).toBeNull();
  });
});

describe("formatEta", () => {
  const words = { etaSeconds: "About {s} s left", etaMinutes: "About {m} min left" };
  it("rounds the way a person says it", () => {
    expect(formatEta(4_200, words)).toBe("About 4 s left");
    expect(formatEta(42_000, words)).toBe("About 40 s left");
    expect(formatEta(150_000, words)).toBe("About 3 min left");
  });
});

describe("runsByStudio", () => {
  it("counts runs and failures per Studio, most used first", () => {
    const run = (studioKey: string, status: Run["status"]) => ({ studioKey, status }) as Run;
    expect(runsByStudio([run("a", "SUCCEEDED"), run("b", "FAILED"), run("b", "SUCCEEDED")])).toEqual([
      { studioKey: "b", runs: 2, failed: 1 },
      { studioKey: "a", runs: 1, failed: 0 },
    ]);
  });
});
