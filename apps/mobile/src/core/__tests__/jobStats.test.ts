import type { JobRecord } from "@krizaka/orazaka-shared";
import { jobKind, jobsByKind, jobsPerDay } from "../jobStats";

const job = (over: Partial<JobRecord>): JobRecord => ({
  id: "j",
  userId: "u",
  featureKey: "orazaka.core.media.image",
  status: "COMPLETED",
  payload: {},
  result: {},
  createdAt: "2026-10-11T10:00:00Z",
  updatedAt: "2026-10-11T10:01:00Z",
  ...over,
});

describe("jobStats", () => {
  it("names the kind of work by a key the screen words", () => {
    expect(jobKind("orazaka.core.media.speech")).toBe("voice");
    expect(jobKind("orazaka.studio.media.sound")).toBe("voice");
    expect(jobKind("orazaka.core.chat.completion")).toBe("text");
  });

  it("lays out every day of the window, failures apart", () => {
    const rows = jobsPerDay(
      [job({}), job({ status: "FAILED" }), job({ createdAt: "2026-09-01T10:00:00Z" })],
      3,
      new Date("2026-10-11T12:00:00Z"),
    );
    expect(rows).toHaveLength(3);
    expect(rows[2]).toMatchObject({ done: 1, failed: 1 });
  });

  it("counts the kinds, largest first", () => {
    expect(jobsByKind([job({}), job({}), job({ featureKey: "x.video" })])).toEqual([
      { kind: "image", count: 2 },
      { kind: "video", count: 1 },
    ]);
  });
});
