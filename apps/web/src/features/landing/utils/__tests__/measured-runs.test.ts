import fs from "node:fs";
import path from "node:path";
import { measuredRuns } from "@/features/landing/utils/measured-runs";

const TABLE = `# Measured runs

What the models really did, measured on **2026-10-17**: each figure is read from the run records.

## Per model

| Model | Where | Runs (ok / all) | Time, command to file (median, min–max) | Engine time | Price | Spent |
| --- | --- | --- | --- | --- | --- | --- |
| **Wan 2.2 T2V A14B** (video) | DigitalOcean serverless inference | 21 / 21 | 131 s (129–203, n=18) | — | $0.60 per video | $12.60 |
| **FLUX.2 [klein] 4B (Q8_0 GGUF)** (image) | One Mac (Metal), local | 6 / 18 | 62 s (61–66, n=6) | 72.7 s | none | — |
| **Stable Diffusion 3.5 Large** (image) | DigitalOcean serverless inference | 51 / 51 | 8 s (7–24, n=51) | — | $0.08 per image | $4.08 |

## Parameters
`;

describe("measuredRuns", () => {
  it("counts the runs that succeeded, every run recorded, the models and the day, from the generated table", () => {
    expect(measuredRuns(TABLE)).toEqual({ ok: 78, all: 90, models: 3, day: "2026-10-17" });
  });

  it("is empty rather than wrong when the page has no table", () => {
    expect(measuredRuns("# Measured runs\n\nNothing yet.")).toEqual({ ok: 0, all: 0, models: 0, day: null });
  });

  it("reads the real published page", () => {
    const md = fs.readFileSync(path.join(process.cwd(), "content", "docs", "measured-runs.md"), "utf8");
    const m = measuredRuns(md);
    expect(m.models).toBeGreaterThan(5);
    expect(m.all).toBeGreaterThanOrEqual(m.ok);
    expect(m.ok).toBeGreaterThan(50);
    expect(m.day).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});
